package ua.lnu.doaj.http;

/** Сайт не пустив нас (Cloudflare або обмеження частоти) навіть після кількох повторів. */
public class BlockedException extends RuntimeException {
    public BlockedException(String message) {
        super(message);
    }
}
