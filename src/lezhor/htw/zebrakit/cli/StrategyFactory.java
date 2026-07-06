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
        Strategy build(NetworkClient client, String navOverride);
    }

    private static final Map<String, Builder> REGISTRY = Map.of(
            "Idle", (client, navOverride) -> new IdleStrategy(),
            "Dummy", (client, navOverride) -> new DummyStrategy(),
            "RandomWalk", (client, navOverride) -> new RandomWalkStrategy(),
            "RandomTarget", (client, navOverride) ->
                    new RandomTargetStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "PowerupHunt", (client, navOverride) ->
                    new PowerupHuntStrategy(NavigatorFactory.createInitialized(navOverride != null ? navOverride : "Theta", client)),
            "TerritoryPaint", (client, navOverride) -> {
                Navigator powerupMovement = NavigatorFactory.createInitialized("Theta", client);
                MovementProvider territoryMovement =
                        NavigatorFactory.createInitialized(navOverride != null ? navOverride : "ColorAStar", client);
                return new TerritoryPaintStrategy(territoryMovement, powerupMovement);
            }
    );

    public static Set<String> availableNames() {
        return REGISTRY.keySet();
    }

    public static Strategy create(String name, NetworkClient client, String navOverride) {
        Builder builder = REGISTRY.get(name);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown strategy: " + name + ". Available: " + availableNames());
        }
        return builder.build(client, navOverride);
    }
}
