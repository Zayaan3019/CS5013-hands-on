import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session 6B solution.
 * Argument format: {@code author|year_from|year_to}, any part may be
 * empty.
 *
 * The parser is minimal on purpose -- the sample catalogue
 * ({@code books.json}) uses a flat JSON-array of flat JSON-objects, so
 * a line-oriented regex scan is enough and keeps the lab independent of
 * any JSON library.
 */
public class SearchBooksTool implements Tool {

    private final Path catalogue;

    public SearchBooksTool() {
        this(Path.of("books.json"));
    }

    public SearchBooksTool(Path catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public String name() {
        return "search_books";
    }

    @Override
    public String call(String arg) {
        try {
            return filter(arg, Files.readString(catalogue));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    static String filter(String arg, String json) {
        String input = arg == null ? "" : arg.trim();
        if (input.length() >= 2 && ((input.startsWith("\"") && input.endsWith("\""))
                || (input.startsWith("'") && input.endsWith("'")))) {
            input = input.substring(1, input.length() - 1);
        }
        String[] parts = input.split("\\|", -1);
        if (parts.length > 3) throw new IllegalArgumentException("Expected author|year_from|year_to");
        String author = parts.length > 0 ? parts[0].trim() : "";
        int yFrom = parse(parts, 1, Integer.MIN_VALUE);
        int yTo   = parse(parts, 2, Integer.MAX_VALUE);
        if (yFrom > yTo) throw new IllegalArgumentException("year_from must not exceed year_to");

        // Each record occupies a single line in books.json; splitting
        // the array by },{ gives us the per-record JSON fragments.
        List<String> matched = new ArrayList<>();
        Pattern recPat = Pattern.compile("\\{[^}]+\\}");
        Matcher m = recPat.matcher(json);
        while (m.find()) {
            String rec = m.group();
            if (!author.isEmpty() && !containsField(rec, "author", author)) continue;
            int year = fieldInt(rec, "year");
            if (year < yFrom || year > yTo) continue;
            matched.add(rec);
        }
        return "[" + String.join(",", matched) + "]";
    }

    private static int parse(String[] parts, int i, int fallback) {
        if (parts.length <= i || parts[i].isBlank()) return fallback;
        try { return Integer.parseInt(parts[i].trim()); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid year: " + parts[i].trim());
        }
    }

    private static boolean containsField(String rec, String key, String needle) {
        Matcher m = Pattern
            .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(rec);
        if (!m.find()) return false;
        String value = m.group(1).toLowerCase(Locale.ROOT);
        String query = needle.toLowerCase(Locale.ROOT);
        String normalizedQuery = withoutInitials(query);
        return value.contains(query) || (!normalizedQuery.isEmpty()
                && withoutInitials(value).contains(normalizedQuery));
    }

    // Allow omitted middle initials, e.g. Ursula Le Guin / Ursula K. Le Guin.
    private static String withoutInitials(String name) {
        return name.replaceAll("\\b\\p{L}\\.?(?=\\s|$)", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static int fieldInt(String rec, String key) {
        Matcher m = Pattern
            .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
            .matcher(rec);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }
}
