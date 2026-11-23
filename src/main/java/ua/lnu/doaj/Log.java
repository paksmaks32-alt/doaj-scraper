package ua.lnu.doaj;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Простий журнал подій: пише і в консоль, і у файл scraper.log. */
public final class Log {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Вивід у консоль в UTF-8, щоб кирилиця не перетворювалась на "????". */
    private static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private Log() {}

    public static synchronized void info(String message) {
        String line = LocalDateTime.now().format(FMT) + "  " + message;
        OUT.println(line);
        try {
            Files.createDirectories(Settings.LOG_FILE.getParent());
            Files.writeString(Settings.LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // якщо лог-файл недоступний, консолі достатньо
        }
    }
}
