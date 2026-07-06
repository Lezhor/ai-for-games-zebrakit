package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * Repels a bot from nearby walls. Casts rays outward in several directions and,
 * for the nearest wall found within {@code avoidRadiusPx} along each, adds a
 * push in the opposite direction that grows <em>quadratically</em> as the wall
 * gets closer — gentle at the edge of the radius, overwhelming right next to a
 * wall. The returned vector is intentionally <em>not</em> normalized: its
 * magnitude signals urgency, so a caller weighting it highly can let it
 * override path-following and value-seeking near a wall (and un-stick a bot the
 * server has pinned against one). Zero when no wall is within the radius.
 */
public class WallAvoidanceBehavior implements SteeringBehavior {
    private final double avoidRadiusPx;
    private final int rayCount;
    private final int stepPx;

    public WallAvoidanceBehavior(double avoidRadiusPx, int rayCount, int stepPx) {
        this.avoidRadiusPx = avoidRadiusPx;
        this.rayCount = rayCount;
        this.stepPx = stepPx;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        Vector2 push = Vector2.ZERO;
        for (int r = 0; r < rayCount; r++) {
            double angle = 2 * Math.PI * r / rayCount;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            for (int d = stepPx; d <= avoidRadiusPx; d += stepPx) {
                int sx = currentPos.x + (int) (dx * d);
                int sy = currentPos.y + (int) (dy * d);
                if (!state.isWalkable(sx, sy)) {
                    double closeness = (avoidRadiusPx - d) / avoidRadiusPx; // 0 at the radius, 1 at the bot
                    double strength = closeness * closeness;
                    push = push.plus(new Vector2(-dx, -dy).scaled(strength));
                    break; // only the nearest wall along this ray
                }
            }
        }
        return push;
    }
}
