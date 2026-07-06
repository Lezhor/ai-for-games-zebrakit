package lezhor.htw.zebrakit.cli;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.movement.nav.ColorAStarNavigator;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.movement.nav.ThetaStarNavigator;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Name-to-constructor registry for {@link Navigator} implementations. Add a new one here to make it CLI-selectable. */
public final class NavigatorFactory {
    private NavigatorFactory() {
    }

    // scale=4 → pathfind on a 256x256 grid; low-res pathfinding is fine for this game and much cheaper.
    // inflation=12px keeps paths off walls; reactive wall-avoidance steering adds the rest of the margin.
    private static final Map<String, Supplier<Navigator>> REGISTRY = Map.of(
            "Theta", () -> new ThetaStarNavigator(12, 4),
            "ColorAStar", () -> new ColorAStarNavigator(12, 4)
    );

    public static Set<String> availableNames() {
        return REGISTRY.keySet();
    }

    public static Navigator create(String name) {
        Supplier<Navigator> supplier = REGISTRY.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown navigator: " + name + ". Available: " + availableNames());
        }
        return supplier.get();
    }

    public static Navigator createInitialized(String name, NetworkClient client) {
        Navigator navigator = create(name);
        navigator.initialize(client);
        return navigator;
    }
}
