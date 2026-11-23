package ua.lnu.doaj.http;

import ua.lnu.doaj.Log;
import ua.lnu.doaj.Settings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP-клієнт для DOAJ. Відповідає за три речі:
 * 1) пауза між запитами (не частіше ніж раз на REQUEST_INTERVAL);
 * 2) "браузерні" заголовки;
 * 3) повтори з наростаючою паузою, якщо сервер відповів помилкою або спрацював Cloudflare.
 */
public class DoajClient {

    /** Результат запиту: код відповіді і текст тіла. */
    public record Response(int status, String body) {}

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private long lastRequestAt = 0;
    private long requestCount = 0;

    /** Пошуковий запит. sourceJson — JSON-запит до Elasticsearch (параметр source з адреси). */
    public String search(String sourceJson) throws InterruptedException {
        String url = Settings.BASE_URL + Settings.SEARCH_PATH
                + "?ref=public_journal&source=" + URLEncoder.encode(sourceJson, StandardCharsets.UTF_8);
        Response r = get(url, "application/json, text/javascript, */*; q=0.01", true);
        if (r.status() != 200) {
            throw new IllegalStateException("Пошук повернув HTTP " + r.status() + ": " + preview(r.body()));
        }
        return stripJsonp(r.body());
    }

    /** Завантажити HTML-сторінку журналу /toc/{issn}. 404 повертаємо як є, без повторів. */
    public Response tocPage(String issn) throws InterruptedException {
        String url = Settings.BASE_URL + Settings.TOC_PATH + URLEncoder.encode(issn, StandardCharsets.UTF_8);
        return get(url, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", false);
    }

    public long requestCount() {
        return requestCount;
    }

    private Response get(String url, String accept, boolean ajax) throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            throttle();
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", Settings.USER_AGENT)
                    .header("Accept", accept)
                    .header("Accept-Language", "en-US,en;q=0.9,uk;q=0.8")
                    .header("Referer", Settings.BASE_URL + "/search/journals");
            if (ajax) {
                b.header("X-Requested-With", "XMLHttpRequest");
            }

            HttpResponse<String> resp;
            try {
                resp = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // обрив зв'язку, таймаут тощо — повторюємо
                waitBeforeRetry(attempt, "мережева помилка: " + e.getMessage(), null);
                continue;
            }
            requestCount++;

            int status = resp.statusCode();
            String body = resp.body();

            if (status == 200 && !isCloudflareChallenge(body, resp)) {
                return new Response(status, body);
            }
            if (status == 404) {
                return new Response(status, body);
            }
            if (isCloudflareChallenge(body, resp)) {
                waitBeforeRetry(attempt, "Cloudflare challenge (HTTP " + status + ")", null);
            } else if (status == 429 || status >= 500) {
                waitBeforeRetry(attempt, "HTTP " + status, resp.headers().firstValue("Retry-After"));
            } else {
                // 400, 403 без Cloudflare тощо — повтор не допоможе
                return new Response(status, body);
            }
        }
    }

    /** Пауза між запитами, щоб не перевищувати 1 запит/с. */
    private void throttle() throws InterruptedException {
        long waitMs = lastRequestAt + Settings.REQUEST_INTERVAL.toMillis() - System.currentTimeMillis();
        if (waitMs > 0) Thread.sleep(waitMs);
        lastRequestAt = System.currentTimeMillis();
    }

    /** Повтор з наростаючою паузою: 15 с, 30 с, 60 с, 120 с. Після MAX_RETRIES — зупинка. */
    private void waitBeforeRetry(int attempt, String reason, Optional<String> retryAfter)
            throws InterruptedException {
        if (attempt >= Settings.MAX_RETRIES) {
            throw new BlockedException("Сайт не відповідає належним чином після "
                    + Settings.MAX_RETRIES + " повторів (" + reason + ")");
        }
        long seconds = 15L << attempt;
        if (retryAfter != null && retryAfter.isPresent()) {
            try {
                seconds = Math.max(seconds, Long.parseLong(retryAfter.get().trim()));
            } catch (NumberFormatException ignored) { }
        }
        Log.info("  ! " + reason + " — чекаю " + seconds + " с і пробую ще раз (спроба "
                + (attempt + 2) + ")");
        Thread.sleep(seconds * 1000);
    }

    /** Ознаки сторінки перевірки Cloudflare ("Just a moment...", "Підтвердьте, що ви людина"). */
    static boolean isCloudflareChallenge(String body, HttpResponse<?> resp) {
        if (resp != null && resp.headers().firstValue("cf-mitigated").isPresent()) return true;
        if (body == null) return false;
        String head = body.length() > 4000 ? body.substring(0, 4000) : body;
        return head.contains("challenge-platform") || head.contains("cf-chl")
                || head.contains("<title>Just a moment");
    }

    /**
     * Сайт відповідає у форматі JSONP: callbackName({...}). Якщо обгортка є — знімаємо її,
     * щоб отримати чистий JSON.
     */
    static String stripJsonp(String body) {
        String t = body.trim();
        if (t.startsWith("{")) return t;
        int open = t.indexOf('(');
        int close = t.lastIndexOf(')');
        if (open > 0 && close > open) return t.substring(open + 1, close);
        return t;
    }

    private static String preview(String body) {
        if (body == null) return "";
        String t = body.replaceAll("\\s+", " ");
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }
}
