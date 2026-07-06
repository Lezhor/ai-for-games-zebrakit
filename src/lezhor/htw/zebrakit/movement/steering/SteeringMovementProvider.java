package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.MovementProvider;

import java.awt.Point;
import java.util.List;

/** Combines a weighted set of {@link SteeringBehavior}s into one movement direction. */
public class SteeringMovementProvider implements MovementProvider {
    private final List<WeightedBehavior> behaviors;

    public SteeringMovementProvider(List<WeightedBehavior> behaviors) {
        this.behaviors = behaviors;
    }

    @Override
    public void initialize(long seed) {
        // Steering has no static map to precompute; it reads GameState live each call.
    }

    @Override
    public void initialize(lenz.htw.zebrakit.net.NetworkClient client) {
        // Same as above.
    }

    @Override
    public Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos) {
        Vector2 sum = Vector2.ZERO;
        for (WeightedBehavior wb : behaviors) {
            sum = sum.plus(wb.behavior().steer(state, self, currentPos, targetPos).scaled(wb.weight()));
        }
        return sum.normalized();
    }

    @Override
    public boolean isReachable(Point currentPos, Point targetPos) {
        // Steering doesn't guarantee reachability the way grid pathfinding does.
        return true;
    }
}
