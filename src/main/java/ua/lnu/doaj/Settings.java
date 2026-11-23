package ua.lnu.doaj;

import java.nio.file.Path;
import java.time.Duration;

/** Усі налаштування програми в одному місці. */
public final class Settings {
    private Settings() {}

    /** Базова адреса сайту. Можна підмінити через -Ddoaj.base=... (для тестів). */
    public static final String BASE_URL = System.getProperty("doaj.base", "https://doaj.org");

    /** Адреса пошуку журналів, яку ми знайшли у вкладці Network. */
    public static final String SEARCH_PATH = "/query/journal/_search";

    /** Адреса сторінки журналу; {issn} підставляється. */
    public static final String TOC_PATH = "/toc/";

    /** Скільки журналів просити за один запит. */
    public static final int PAGE_SIZE = 100;

    /** Мінімальна пауза між будь-якими двома запитами (вимога: не частіше 1 запиту за секунду). */
    public static final Duration REQUEST_INTERVAL = Duration.ofMillis(
            Long.getLong("doaj.intervalMs", 1100));

    /** Скільки разів повторювати запит після помилки, перш ніж зупинитися. */
    public static final int MAX_RETRIES = 4;

    /**
     * Папка з даними: база і HTML-сторінки.
     * За замовчуванням — C:\Users\<ім'я>\doaj-data (не в OneDrive, щоб синхронізація
     * не заважала базі й не качала гігабайти сторінок у хмару).
     */
    public static final Path DATA_DIR = Path.of(System.getProperty("doaj.data",
            Path.of(System.getProperty("user.home"), "doaj-data").toString()));

    public static final Path DB_FILE = DATA_DIR.resolve("doaj.sqlite");
    public static final Path HTML_DIR = DATA_DIR.resolve("html");
    public static final Path LOG_FILE = DATA_DIR.resolve("scraper.log");

    /**
     * Заголовки, з якими ходить звичайний браузер. Без них Cloudflare
     * швидше розпізнає програму як бота.
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
}
