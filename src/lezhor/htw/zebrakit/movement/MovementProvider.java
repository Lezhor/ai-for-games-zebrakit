package lezhor.htw.zebrakit.movement;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * The single abstraction strategies depend on to get "a direction toward a
 * target" — implemented by grid pathfinding ({@code movement.nav}), steering
 * behaviors ({@code movement.steering}), or a blend of both
 * ({@link HybridMovementProvider}), so a strategy can swap between them
 * without changing its decision logic.
 */
public interface MovementProvider {
    /** Offline/seed-based initialization (see core.GameState#offline). */
    void initialize(long seed);

    /** Live initialization against a running match. */
    void initialize(lenz.htw.zebrakit.net.NetworkClient client);

    /**
     * Direction the given bot at currentPos should move right now to make
     * progress toward targetPos. Always a UNIT vector, or {@link Vector2#ZERO}
     * if unreachable or already arrived. `state`/`self` give implementations
     * (steering behaviors in particular) access to other bots, powerups and
     * board colors; grid-pathfinding implementations are free to ignore them.
     */
    Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos);

    /** True if targetPos is reachable from currentPos at all. */
    boolean isReachable(Point currentPos, Point targetPos);
}
