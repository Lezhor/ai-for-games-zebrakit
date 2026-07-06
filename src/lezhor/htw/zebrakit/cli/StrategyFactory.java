package lezhor.htw.zebrakit.cli;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.movement.MovementProvider;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.*;

import java.util.Map;
import java.util.Set;

/** Name-to-constructor registry for {@link Strategy} implementations. Add a new one here to make it CLI-selectable. */
public final class StrategyFactory {
    private StrategyFactory() {
    }

    @FunctionalInterface
    private interface Builder {
        Strategy build(NetworkClient client, String navOverride, int matchLengthSeconds);
    }

    private static final Map<String, Builder> REGISTRY = Map.of(
            "Idle", (client, navOverride, matchLength) -> new IdleStrategy(),
            "Dummy", (client, navOverride, matchLength) -> new DummyStrategy(),
            "RandomWalk", (client, navOverride, matchLength) -> new RandomWalkStrategy(),
            "RandomTarget", (client, navOverride, matchLength) ->
                    new RandomTargetStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "PowerupHunt", (client, navOverride, matchLength) ->
                    new PowerupHuntStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "TerritoryPaint", (client, navOverride, matchLength) -> {
                Navigator powerupMovement = NavigatorFactory.createInitialized("Theta", client);
                MovementProvider territoryMovement =
                        NavigatorFactory.createInitialized(navOverride != null ? navOverride : "ColorAStar", client);
                return new TerritoryPaintStrategy(territoryMovement, powerupMovement);
            },
            "TerritoryDiffusion", (client, navOverride, matchLength) -> {
                Navigator territoryNav = NavigatorFactory.createInitialized(navOverride != null ? navOverride : "ColorAStar", client);
                Navigator powerupNav = NavigatorFactory.createInitialized("Theta", client);
                return new TerritoryDiffusionStrategy(territoryNav, powerupNav, matchLength);
            }
    );

    public static Set<String> availableNames() {
        return REGISTRY.keySet();
    }

    public static Strategy create(String name, NetworkClient client, String navOverride, int matchLengthSeconds) {
        Builder builder = REGISTRY.get(name);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown strategy: " + name + ". Available: " + availableNames());
        }
        return builder.build(client, navOverride, matchLengthSeconds);
    }
}
