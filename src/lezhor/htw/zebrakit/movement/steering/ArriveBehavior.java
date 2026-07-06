package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * Steers toward the target, decelerating within {@code slowingRadiusPx} of it.
 * Combined with {@link SeparationBehavior}, this is what lets bots settle
 * around a shared destination instead of stacking exactly on it.
 */
public class ArriveBehavior implements SteeringBehavior {
    private final double slowingRadiusPx;

    public ArriveBehavior(double slowingRadiusPx) {
        this.slowingRadiusPx = slowingRadiusPx;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        Vector2 toTarget = Vector2.towards(currentPos, target);
        double dist = toTarget.length();
        if (dist == 0) return Vector2.ZERO;
        double speedFactor = Math.min(1.0, dist / slowingRadiusPx);
        return toTarget.normalized().scaled(speedFactor);
    }
}
