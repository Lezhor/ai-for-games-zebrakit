package lezhor.htw.zebrakit.cli;

/** Pragmatic default values for process bootstrapping — no per-strategy flavor text. */
public final class Defaults {
    public static final String WIN_MESSAGE = "gg";
    public static final String HOST = "127.0.0.1";
    public static final String PORT = "22135";
    /** Assumed match length; drives time-aware strategies. Override with --time. Matches the server's default. */
    public static final int MATCH_LENGTH_SECONDS = 60;

    private Defaults() {
    }
}
