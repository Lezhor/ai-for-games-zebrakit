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

    private static final Map<String, Supplier<Navigator>> REGISTRY = Map.of(
            "Theta", () -> new ThetaStarNavigator(8, 2),
            "ColorAStar", () -> new ColorAStarNavigator(8, 2)
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
