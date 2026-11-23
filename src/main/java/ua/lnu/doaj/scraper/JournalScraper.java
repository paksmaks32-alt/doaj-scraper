package ua.lnu.doaj.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ua.lnu.doaj.Log;
import ua.lnu.doaj.Settings;
import ua.lnu.doaj.db.JournalRepository;
import ua.lnu.doaj.http.DoajClient;
import ua.lnu.doaj.model.Hit;
import ua.lnu.doaj.model.Journal;
import ua.lnu.doaj.model.SearchResponse;

import java.util.List;

/**
 * Етап 1: зібрати верхньорівневу інформацію про всі журнали.
 *
 * Гортання через search_after. Звичайне гортання ("from": 10000) Elasticsearch
 * зазвичай не дозволяє далі 10 000-го результату, а журналів понад 23 тисячі.
 * search_after натомість каже: "дай наступні 100 журналів ПІСЛЯ ось цього" —
 * де "цей" задається значеннями сортування останнього журналу попередньої сторінки.
 * Щоб сортування було однозначним, крім дати додаємо другий ключ — id журналу.
 */
public class JournalScraper {

    private static final String META_DONE = "journals_done";
    private static final String META_CURSOR = "journals_search_after";

    private final DoajClient client;
    private final JournalRepository repo;
    private final ObjectMapper mapper;

    /** Чи використовувати id як другий ключ сортування (вимикається, якщо сервер його не приймає). */
    private boolean useTiebreaker = true;

    public JournalScraper(DoajClient client, JournalRepository repo, ObjectMapper mapper) {
        this.client = client;
        this.repo = repo;
        this.mapper = mapper;
    }

    public void run(boolean refresh) throws Exception {
        if (refresh) {
            repo.setMeta(META_DONE, null);
            repo.setMeta(META_CURSOR, null);
        }
        if ("true".equals(repo.getMeta(META_DONE))) {
            Log.info("Етап 1 вже завершено раніше (" + repo.countJournals()
                    + " журналів у базі). Для повторного збору запустіть з аргументом --refresh.");
            return;
        }

        String cursorJson = repo.getMeta(META_CURSOR);
        JsonNode cursor = cursorJson == null ? null : mapper.readTree(cursorJson);
        if (cursor != null) {
            Log.info("Продовжую з місця зупинки. Уже в базі: " + repo.countJournals());
            useTiebreaker = cursor.size() > 1;
        }

        long total = -1;
        int page = 0;
        while (true) {
            page++;
            SearchResponse response = fetchPage(cursor);
            List<Hit> hits = response.hits.hits;
            if (total < 0) {
                total = response.hits.total.value;
                Log.info("Усього журналів у DOAJ: " + total);
            }
            if (hits == null || hits.isEmpty()) break;

            int added = 0;
            repo.begin();
            for (Hit hit : hits) {
                Journal journal = mapper.treeToValue(hit.rawSource, Journal.class);
                if (journal.id == null) journal.id = hit.id;
                long createdMillis = ((Number) hit.sort.get(0)).longValue();
                if (repo.upsert(journal, hit.rawSource, createdMillis)) added++;
            }
            // курсор = значення сортування останнього журналу на сторінці
            cursor = mapper.valueToTree(hits.get(hits.size() - 1).sort);
            repo.setMeta(META_CURSOR, cursor.toString());
            repo.commit();

            long inDb = repo.countJournals();
            Log.info(String.format("Сторінка %d: отримано %d, нових %d. У базі %d з %d (%.1f%%)",
                    page, hits.size(), added, inDb, total, 100.0 * inDb / total));

            if (hits.size() < Settings.PAGE_SIZE) break;   // остання сторінка
        }

        repo.setMeta(META_DONE, "true");
        long inDb = repo.countJournals();
        Log.info("Етап 1 завершено. У базі " + inDb + " журналів, DOAJ повідомляє про " + total + ".");
        if (inDb < total) {
            Log.info("  Увага: зібрано менше, ніж очікувалось, на " + (total - inDb)
                    + ". Можливо, частину журналів додали/видалили під час збору.");
        }
    }

    private SearchResponse fetchPage(JsonNode cursor) throws Exception {
        try {
            return mapper.readValue(client.search(buildQuery(cursor)), SearchResponse.class);
        } catch (IllegalStateException e) {
            // Якщо сервер не приймає сортування за id — пробуємо лише за датою.
            if (useTiebreaker && cursor == null && e.getMessage().contains("HTTP 400")) {
                Log.info("  Сервер не прийняв сортування за id, перемикаюсь на сортування лише за датою.");
                useTiebreaker = false;
                return mapper.readValue(client.search(buildQuery(null)), SearchResponse.class);
            }
            throw e;
        }
    }

    /**
     * JSON-запит до Elasticsearch. Це той самий запит, що робить сторінка пошуку DOAJ
     * (див. параметр source у вкладці Network), але без агрегацій для фільтрів.
     */
    String buildQuery(JsonNode cursor) {
        ObjectNode q = mapper.createObjectNode();
        q.putObject("query").putObject("match_all");
        q.put("size", Settings.PAGE_SIZE);
        ArrayNode sort = q.putArray("sort");
        sort.addObject().putObject("created_date").put("order", "desc");
        if (useTiebreaker) {
            sort.addObject().putObject("id.exact").put("order", "asc");
        }
        q.put("track_total_hits", true);
        if (cursor != null) {
            q.set("search_after", cursor);
        }
        return q.toString();
    }
}
