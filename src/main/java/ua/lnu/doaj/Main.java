package ua.lnu.doaj;

import com.fasterxml.jackson.databind.ObjectMapper;
import ua.lnu.doaj.db.JournalRepository;
import ua.lnu.doaj.http.BlockedException;
import ua.lnu.doaj.http.DoajClient;
import ua.lnu.doaj.scraper.JournalScraper;
import ua.lnu.doaj.scraper.TocDownloader;

import java.util.Arrays;
import java.util.List;

/**
 * Точка входу.
 *   без аргументів  — етап 1 (журнали), потім етап 2 (HTML-сторінки)
 *   journals        — лише етап 1
 *   toc             — лише етап 2
 *   --refresh       — зібрати журнали заново, навіть якщо етап 1 уже завершено
 */
public class Main {
    public static void main(String[] args) {
        List<String> a = Arrays.asList(args);
        boolean stage1 = !a.contains("toc");
        boolean stage2 = !a.contains("journals");
        boolean refresh = a.contains("--refresh");

        Log.info("=== DOAJ scraper. Дані зберігаються в: " + Settings.DATA_DIR.toAbsolutePath());
        ObjectMapper mapper = new ObjectMapper();
        DoajClient client = new DoajClient();
        long started = System.currentTimeMillis();

        int exitCode = 0;
        try (JournalRepository repo = new JournalRepository(Settings.DB_FILE)) {
            if (stage1) new JournalScraper(client, repo, mapper).run(refresh);
            if (stage2) new TocDownloader(client, repo).run();
        } catch (BlockedException e) {
            Log.info("ЗУПИНКА: " + e.getMessage());
            Log.info("Прогрес збережено. Запустіть програму пізніше — вона продовжить з місця зупинки.");
            exitCode = 2;
        } catch (Exception e) {
            Log.info("ПОМИЛКА: " + e);
            e.printStackTrace();
            exitCode = 1;
        }
        long sec = (System.currentTimeMillis() - started) / 1000;
        Log.info("Запитів зроблено: " + client.requestCount() + ", час роботи: "
                + sec / 60 + " хв " + sec % 60 + " с");
        System.exit(exitCode);
    }
}
