package lezhor.htw.zebrakit.cli;

/**
 * Parsed CLI configuration. `port` is accepted for CLI symmetry with
 * `scripts/server.sh` but unused — {@code NetworkClient}'s constructor takes
 * no port, the game server always listens on {@link Defaults#PORT}.
 */
public record RunConfig(String strategyName, String botName, String host, String port, String navOverride) {
}
