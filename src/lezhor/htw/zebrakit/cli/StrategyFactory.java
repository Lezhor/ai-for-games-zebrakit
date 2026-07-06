package lezhor.htw.zebrakit.cli;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.movement.MovementProvider;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.*;
import lezhor.htw.zebrakit.strategy.bresenham.BresenhamConfig;
import lezhor.htw.zebrakit.strategy.support.IniConfig;
import lezhor.htw.zebrakit.strategy.support.StrategyConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;

/** Name-to-constructor registry for {@link Strategy} implementations. Add a new one here to make it CLI-selectable. */
public final class StrategyFactory {
    private StrategyFactory() {
    }

    @FunctionalInterface
    private interface Builder {
        Strategy build(NetworkClient client, String navOverride, int matchLengthSeconds, String strategyConfigPath);
    }

    private static final Map<String, Builder> REGISTRY = Map.of(
            "Idle", (client, navOverride, matchLength, configPath) -> new IdleStrategy(),
            "Dummy", (client, navOverride, matchLength, configPath) -> new DummyStrategy(),
            "RandomWalk", (client, navOverride, matchLength, configPath) -> new RandomWalkStrategy(),
            "RandomTarget", (client, navOverride, matchLength, configPath) ->
                    new RandomTargetStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "PowerupHunt", (client, navOverride, matchLength, configPath) ->
                    new PowerupHuntStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "TerritoryPaint", (client, navOverride, matchLength, configPath) -> {
                Navigator powerupMovement = NavigatorFactory.createInitialized("Theta", client);
                MovementProvider territoryMovement =
                        NavigatorFactory.createInitialized(navOverride != null ? navOverride : "ColorAStar", client);
                return new TerritoryPaintStrategy(territoryMovement, powerupMovement);
            },
            "TerritoryDiffusion", (client, navOverride, matchLength, configPath) -> {
                Navigator territoryNav = NavigatorFactory.createInitialized(navOverride != null ? navOverride : "ColorAStar", client);
                Navigator powerupNav = NavigatorFactory.createInitialized("Theta", client);
                StrategyConfig strategyConfig = loadStrategyConfig(configPath);
                return new TerritoryDiffusionStrategy(territoryNav, powerupNav, matchLength, strategyConfig);
            },
            "Bresenham", (client, navOverride, matchLength, configPath) -> {
                Navigator powerupNav = NavigatorFactory.createInitialized("Theta", client);
                return new BresenhamStrategy(powerupNav, BresenhamConfig.from(loadIniConfig(configPath)));
            }
    );

    public static Set<String> availableNames() {
        return REGISTRY.keySet();
    }

    public static Strategy create(String name, NetworkClient client, String navOverride, int matchLengthSeconds,
                                   String strategyConfigPath) {
        Builder builder = REGISTRY.get(name);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown strategy: " + name + ". Available: " + availableNames());
        }
        return builder.build(client, navOverride, matchLengthSeconds, strategyConfigPath);
    }

    private static StrategyConfig loadStrategyConfig(String strategyConfigPath) {
        if (strategyConfigPath == null) return null;
        try {
            return StrategyConfig.load(strategyConfigPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load --config " + strategyConfigPath, e);
        }
    }

    private static IniConfig loadIniConfig(String strategyConfigPath) {
        if (strategyConfigPath == null) return null;
        try {
            return IniConfig.load(strategyConfigPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load --config " + strategyConfigPath, e);
        }
    }
}
