package ua.lnu.doaj.db;

import com.fasterxml.jackson.databind.JsonNode;
import ua.lnu.doaj.model.Bibjson;
import ua.lnu.doaj.model.Journal;
import ua.lnu.doaj.model.License;
import ua.lnu.doaj.model.Subject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Робота з базою даних SQLite через JDBC.
 * Таблиця journals — по рядку на журнал; таблиця meta — службові позначки (наприклад,
 * що етап 1 завершено).
 */
public class JournalRepository implements AutoCloseable {

    private final Connection conn;

    public JournalRepository(Path dbFile) throws Exception {
        Files.createDirectories(dbFile.getParent());
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS journals (
                    id                  TEXT PRIMARY KEY,   -- внутрішній id DOAJ
                    title               TEXT NOT NULL,
                    alternative_title   TEXT,
                    eissn               TEXT,
                    pissn               TEXT,
                    publisher           TEXT,
                    publisher_country   TEXT,
                    institution         TEXT,
                    institution_country TEXT,
                    languages           TEXT,               -- через кому: "EN, UK"
                    keywords            TEXT,
                    subjects            TEXT,
                    licenses            TEXT,
                    has_apc             INTEGER,            -- 1 = є плата за публікацію
                    oa_start            INTEGER,            -- рік початку відкритого доступу
                    journal_url         TEXT,
                    created_date        TEXT,               -- коли журнал додано в DOAJ
                    created_millis      INTEGER,
                    last_updated        TEXT,
                    raw_json            TEXT NOT NULL,      -- повний JSON журналу без втрат
                    scraped_at          TEXT NOT NULL,
                    toc_status          TEXT,               -- ok / not_found / no_issn / error
                    toc_path            TEXT,
                    toc_fetched_at      TEXT
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_journals_created ON journals(created_millis)");
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");
        }
    }

    /**
     * Зберегти журнал: INSERT, а якщо такий id вже є — оновити дані (UPSERT).
     * Колонки toc_* не чіпаємо, щоб не загубити прогрес етапу 2.
     * @return true, якщо журнал новий
     */
    public boolean upsert(Journal j, JsonNode raw, long createdMillis) throws SQLException {
        boolean existed = exists(j.id);
        Bibjson b = j.bibjson;
        String sql = """
            INSERT INTO journals (id, title, alternative_title, eissn, pissn, publisher, publisher_country,
                institution, institution_country, languages, keywords, subjects, licenses, has_apc, oa_start,
                journal_url, created_date, created_millis, last_updated, raw_json, scraped_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                title=excluded.title, alternative_title=excluded.alternative_title,
                eissn=excluded.eissn, pissn=excluded.pissn, publisher=excluded.publisher,
                publisher_country=excluded.publisher_country, institution=excluded.institution,
                institution_country=excluded.institution_country, languages=excluded.languages,
                keywords=excluded.keywords, subjects=excluded.subjects, licenses=excluded.licenses,
                has_apc=excluded.has_apc, oa_start=excluded.oa_start, journal_url=excluded.journal_url,
                created_date=excluded.created_date, created_millis=excluded.created_millis,
                last_updated=excluded.last_updated, raw_json=excluded.raw_json, scraped_at=excluded.scraped_at
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, j.id);
            ps.setString(i++, b.title);
            ps.setString(i++, b.alternativeTitle);
            ps.setString(i++, b.eissn);
            ps.setString(i++, b.pissn);
            ps.setString(i++, b.publisher != null ? b.publisher.name : null);
            ps.setString(i++, b.publisher != null ? b.publisher.country : null);
            ps.setString(i++, b.institution != null ? b.institution.name : null);
            ps.setString(i++, b.institution != null ? b.institution.country : null);
            ps.setString(i++, join(b.language));
            ps.setString(i++, join(b.keywords));
            ps.setString(i++, b.subject == null ? null
                    : join(b.subject.stream().map((Subject s) -> s.term).collect(Collectors.toList())));
            ps.setString(i++, b.license == null ? null
                    : join(b.license.stream().map((License l) -> l.type).collect(Collectors.toList())));
            if (b.apc != null && b.apc.hasApc != null) ps.setInt(i++, b.apc.hasApc ? 1 : 0);
            else ps.setNull(i++, Types.INTEGER);
            if (b.oaStart != null) ps.setInt(i++, b.oaStart);
            else ps.setNull(i++, Types.INTEGER);
            ps.setString(i++, b.ref != null ? b.ref.journal : null);
            ps.setString(i++, j.createdDate);
            ps.setLong(i++, createdMillis);
            ps.setString(i++, j.lastUpdated);
            ps.setString(i++, raw.toString());
            ps.setString(i++, Instant.now().toString());
            ps.executeUpdate();
        }
        return !existed;
    }

    private boolean exists(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM journals WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long countJournals() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM journals")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Транзакції: одна сторінка з 100 журналів записується разом — так швидше і надійніше. */
    public void begin() throws SQLException { conn.setAutoCommit(false); }
    public void commit() throws SQLException { conn.commit(); conn.setAutoCommit(true); }

    // ---------- службові позначки (прогрес) ----------

    public String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    // ---------- етап 2: сторінки журналів ----------

    public record TocTask(String id, String issn) {}

    /** Журнали, сторінки яких ще не завантажені (або завантаження впало з помилкою). */
    public List<TocTask> pendingTocTasks() throws SQLException {
        List<TocTask> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT id, COALESCE(NULLIF(eissn, ''), NULLIF(pissn, '')) AS issn
                 FROM journals
                 WHERE toc_status IS NULL OR toc_status = 'error'
                 ORDER BY created_millis DESC""")) {
            while (rs.next()) list.add(new TocTask(rs.getString("id"), rs.getString("issn")));
        }
        return list;
    }

    public void markToc(String id, String status, String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE journals SET toc_status = ?, toc_path = ?, toc_fetched_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, path);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, id);
            ps.executeUpdate();
        }
    }

    public String tocSummary() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(toc_status, 'ще не оброблено'), COUNT(*) FROM journals GROUP BY 1 ORDER BY 2 DESC")) {
            while (rs.next()) sb.append(rs.getString(1)).append(": ").append(rs.getLong(2)).append("; ");
        }
        return sb.toString();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join(", ", values);
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
