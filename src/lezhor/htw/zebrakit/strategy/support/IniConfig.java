package lezhor.htw.zebrakit.strategy.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@code [Section]}-aware {@code key=value} config file, optionally handed to
 * a strategy via {@code --config <path>}. Unlike the flat {@link StrategyConfig}
 * (which treats {@code [} lines as comments), this parser groups keys under the
 * most recent {@code [Section]} header, so the same key name (e.g. {@code WEIGHT},
 * {@code GRID_SCALE}) can appear once per section without colliding — one section
 * per evaluator, plus a general one at the top.
 *
 * <p>Keys that appear before the first {@code [Section]} (such as the mandatory
 * {@code type} line) live in the top-level section {@code ""}. A {@code #} or
 * {@code ;} starts a comment — whole-line or trailing (e.g. {@code KEY=5  # note}) —
 * and the rest of the line is ignored; blank lines are ignored. Every getter falls
 * back to the caller's own default when the section or key is absent, so a config
 * only needs to mention the values it wants to override.
 */
public class IniConfig {
    private static final String TOP_SECTION = "";
    private static final String TYPE_KEY = "type";

    /** section name -> (key -> value). */
    private final Map<String, Map<String, String>> sections = new HashMap<>();

    public static IniConfig load(String path) throws IOException {
        IniConfig config = new IniConfig();
        String section = TOP_SECTION;
        for (String line : Files.readAllLines(Path.of(path))) {
            String trimmed = stripComment(line).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length() - 1).trim();
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            config.sections
                    .computeIfAbsent(section, s -> new HashMap<>())
                    .put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return config;
    }

    /** Exits the process immediately if this config's top-level {@code type} doesn't match. */
    public void requireType(String expectedStrategyName) {
        String actual = get(TOP_SECTION, TYPE_KEY);
        if (!expectedStrategyName.equalsIgnoreCase(actual)) {
            System.err.println("Config type mismatch: expected type=" + expectedStrategyName
                    + " but config declares type=" + actual);
            System.exit(1);
        }
    }

    /** Drops a whole-line or trailing {@code #}/{@code ;} comment, keeping only the content before it. */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        int semi = line.indexOf(';');
        int cut = hash < 0 ? semi : (semi < 0 ? hash : Math.min(hash, semi));
        return cut < 0 ? line : line.substring(0, cut);
    }

    private String get(String section, String key) {
        Map<String, String> s = sections.get(section);
        return s == null ? null : s.get(key);
    }

    public double getDouble(String section, String key, double fallback) {
        String v = get(section, key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    public int getInt(String section, String key, int fallback) {
        String v = get(section, key);
        return v == null ? fallback : Integer.parseInt(v);
    }

    public long getLong(String section, String key, long fallback) {
        String v = get(section, key);
        return v == null ? fallback : Long.parseLong(v);
    }

    public boolean getBoolean(String section, String key, boolean fallback) {
        String v = get(section, key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    public double[] getDoubleArray(String section, String key, double[] fallback) {
        String v = get(section, key);
        if (v == null) return fallback;
        String[] parts = v.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i].trim());
        return result;
    }
}
