package lezhor.htw.zebrakit.cli;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses {@code <strategyName> [--name <botName>] [--host <host>] [--port <port>] [--nav <navName>]}.
 * Bot name defaults to the strategy name when {@code --name} is omitted.
 */
public final class ArgParser {
    private ArgParser() {
    }

    public static boolean isHelpRequested(String[] args) {
        if (args.length == 0) return true;
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) return true;
        }
        return false;
    }

    /** Just the strategy/navigator names, no usage line — for scripts (run.sh/match.sh) to embed in their own --help. */
    public static boolean isListRequested(String[] args) {
        for (String arg : args) {
            if (arg.equals("--list")) return true;
        }
        return false;
    }

    public static void printHelp() {
        System.out.println("Usage: <strategyName> [--name <botName>] [--host <host>] [--port <port>] [--nav <navName>]");
        System.out.println();
        printAvailable();
    }

    public static void printAvailable() {
        System.out.println("Available strategies:");
        StrategyFactory.availableNames().forEach(n -> System.out.println("  - " + n));
        System.out.println();
        System.out.println("Available navigators (for --nav):");
        NavigatorFactory.availableNames().forEach(n -> System.out.println("  - " + n));
    }

    public static RunConfig parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Usage: <strategyName> [--name <botName>] [--host <host>] [--port <port>] [--nav <navName>]");
        }

        String strategyName = args[0];
        Map<String, String> flags = new HashMap<>();
        int i = 1;
        while (i < args.length) {
            String key = args[i];
            if (key.startsWith("--") && i + 1 < args.length) {
                flags.put(key.substring(2), args[i + 1]);
                i += 2;
            } else {
                i++;
            }
        }

        String botName = flags.getOrDefault("name", strategyName);
        String host = flags.getOrDefault("host", Defaults.HOST);
        String port = flags.getOrDefault("port", Defaults.PORT);
        String navOverride = flags.get("nav");

        return new RunConfig(strategyName, botName, host, port, navOverride);
    }
}
