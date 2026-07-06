package lezhor.htw.zebrakit.strategy.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A flat {@code key=value} config file, optionally handed to a strategy via
 * {@code --config <path>}. Lines starting with {@code #}, {@code ;}, or
 * {@code [} (section headers, purely cosmetic — not required or parsed) are
 * comments; blank lines are ignored. Every getter falls back to the caller's
 * own default when the key is absent, so a config only needs to mention the
 * values it wants to override.
 */
public class StrategyConfig {
    private static final String TYPE_KEY = "type";

    private final Map<String, String> values = new HashMap<>();

    public static StrategyConfig load(String path) throws IOException {
        StrategyConfig config = new StrategyConfig();
        for (String line : Files.readAllLines(Path.of(path))) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("[")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            config.values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return config;
    }

    /** Exits the process immediately if this config's declared {@code type} doesn't match. */
    public void requireType(String expectedStrategyName) {
        String actual = values.get(TYPE_KEY);
        if (!expectedStrategyName.equalsIgnoreCase(actual)) {
            System.err.println("Config type mismatch: expected type=" + expectedStrategyName
                    + " but config declares type=" + actual);
            System.exit(1);
        }
    }

    public double getDouble(String key, double fallback) {
        String v = values.get(key);
        if (v == null) return fallback;
        return Double.parseDouble(v);
    }

    public int getInt(String key, int fallback) {
        String v = values.get(key);
        if (v == null) return fallback;
        return Integer.parseInt(v);
    }

    public long getLong(String key, long fallback) {
        String v = values.get(key);
        if (v == null) return fallback;
        return Long.parseLong(v);
    }

    public double[] getDoubleArray(String key, double[] fallback) {
        String v = values.get(key);
        if (v == null) return fallback;
        String[] parts = v.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i].trim());
        return result;
    }
}
