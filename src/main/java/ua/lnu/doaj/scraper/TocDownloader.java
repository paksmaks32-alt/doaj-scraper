package ua.lnu.doaj.scraper;

import ua.lnu.doaj.Log;
import ua.lnu.doaj.Settings;
import ua.lnu.doaj.db.JournalRepository;
import ua.lnu.doaj.http.DoajClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Етап 2: для кожного журналу завантажити сторінку https://doaj.org/toc/{issn}
 * і зберегти її повністю як .html-файл. Використовується eissn, а якщо його нема — pissn.
 * Прогрес пишеться в базу після кожного журналу, тож програму можна зупинити будь-коли.
 */
public class TocDownloader {

    private final DoajClient client;
    private final JournalRepository repo;

    public TocDownloader(DoajClient client, JournalRepository repo) {
        this.client = client;
        this.repo = repo;
    }

    public void run() throws Exception {
        Files.createDirectories(Settings.HTML_DIR);
        List<JournalRepository.TocTask> tasks = repo.pendingTocTasks();
        if (tasks.isEmpty()) {
            Log.info("Етап 2: усі сторінки вже завантажені. " + repo.tocSummary());
            return;
        }
        long etaMin = tasks.size() * Settings.REQUEST_INTERVAL.toMillis() / 60000;
        Log.info("Етап 2: залишилось завантажити " + tasks.size() + " сторінок (орієнтовно "
                + etaMin / 60 + " год " + etaMin % 60 + " хв).");

        int done = 0;
        for (JournalRepository.TocTask task : tasks) {
            done++;
            if (task.issn() == null) {
                repo.markToc(task.id(), "no_issn", null);
                continue;
            }
            DoajClient.Response r = client.tocPage(task.issn());
            if (r.status() == 200) {
                Path file = Settings.HTML_DIR.resolve(task.issn() + ".html");
                Files.writeString(file, r.body(), StandardCharsets.UTF_8);
                repo.markToc(task.id(), "ok", Settings.DATA_DIR.relativize(file).toString());
            } else if (r.status() == 404) {
                repo.markToc(task.id(), "not_found", null);
            } else {
                repo.markToc(task.id(), "error", "HTTP " + r.status());
            }
            if (done % 50 == 0 || done == tasks.size()) {
                Log.info(String.format("Етап 2: %d / %d (%.1f%%)", done, tasks.size(),
                        100.0 * done / tasks.size()));
            }
        }
        Log.info("Етап 2 завершено. " + repo.tocSummary());
    }
}
