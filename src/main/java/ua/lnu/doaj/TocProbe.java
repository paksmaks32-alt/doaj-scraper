package ua.lnu.doaj;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Діагностика: якими способами можна завантажити сторінку журналу /toc/{issn},
 * щоб Cloudflare не показував перевірку. Пробує кілька варіантів запиту
 * і друкує результат кожного. Запускати окремо (зелена стрілка біля main).
 */
public class TocProbe {

    private static final String ISSN = "2675-0805";
    private static final String UA = Settings.USER_AGENT;
    private static final java.io.PrintStream OUT = new java.io.PrintStream(
            new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String toc = Settings.BASE_URL + "/toc/" + ISSN;

        Map<String, String[]> browserHeaders = new LinkedHashMap<>();
        browserHeaders.put("A. Як зараз у програмі", new String[]{
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", "en-US,en;q=0.9,uk;q=0.8",
                "Referer", Settings.BASE_URL + "/search/journals"});
        browserHeaders.put("B. Повний набір заголовків браузера", new String[]{
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language", "en-US,en;q=0.9,uk;q=0.8",
                "Sec-Fetch-Dest", "document",
                "Sec-Fetch-Mode", "navigate",
                "Sec-Fetch-Site", "none",
                "Sec-Fetch-User", "?1",
                "Upgrade-Insecure-Requests", "1",
                "sec-ch-ua", "\"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\", \"Not_A Brand\";v=\"24\"",
                "sec-ch-ua-mobile", "?0",
                "sec-ch-ua-platform", "\"Windows\""});
        browserHeaders.put("C. Як фоновий AJAX-запит (так проходить пошук)", new String[]{
                "Accept", "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With", "XMLHttpRequest",
                "Referer", Settings.BASE_URL + "/search/journals"});

        OUT.println("Перевіряю " + toc + "\n");
        for (Map.Entry<String, String[]> e : browserHeaders.entrySet()) {
            probe(e.getKey() + " [HTTP/2]", toc, e.getValue(), HttpClient.Version.HTTP_2);
            probe(e.getKey() + " [HTTP/1.1]", toc, e.getValue(), HttpClient.Version.HTTP_1_1);
        }
        probe("D. Офіційне API DOAJ (JSON)",
                Settings.BASE_URL + "/api/search/journals/issn%3A" + ISSN,
                new String[]{"Accept", "application/json"}, HttpClient.Version.HTTP_2);
        probe("E. Контроль: пошуковий запит, що працює в програмі",
                Settings.BASE_URL + "/query/journal/_search?ref=public_journal&source="
                        + java.net.URLEncoder.encode("{\"query\":{\"match_all\":{}},\"size\":1}", StandardCharsets.UTF_8),
                new String[]{"Accept", "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With", "XMLHttpRequest"}, HttpClient.Version.HTTP_2);
    }

    private static void probe(String name, String url, String[] headers, HttpClient.Version version)
            throws InterruptedException {
        Thread.sleep(1500); // пауза між запитами
        HttpClient client = HttpClient.newBuilder().version(version)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA);
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        try {
            HttpResponse<String> r = client.send(b.GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = r.body();
            boolean challenge = r.headers().firstValue("cf-mitigated").isPresent()
                    || body.contains("challenge-platform") || body.contains("<title>Just a moment");
            OUT.printf("%-60s HTTP %d  %s  %,d байт  %s%n", name, r.statusCode(),
                    challenge ? "БЛОК" : "ОК  ", body.length(), title(body));
        } catch (Exception ex) {
            OUT.printf("%-60s ПОМИЛКА: %s%n", name, ex.getMessage());
        }
    }

    private static String title(String html) {
        Matcher m = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL).matcher(html);
        return m.find() ? "title=\"" + m.group(1).trim().replaceAll("\\s+", " ") + "\"" : "";
    }
}
