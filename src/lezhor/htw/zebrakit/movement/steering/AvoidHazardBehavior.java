package lezhor.htw.zebrakit.movement.steering;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.Map;

/** Repels from active SLOW powerups (hazards) within {@code radiusPx}. */
public class AvoidHazardBehavior implements SteeringBehavior {
    private final double radiusPx;

    public AvoidHazardBehavior(double radiusPx) {
        this.radiusPx = radiusPx;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        Vector2 push = Vector2.ZERO;
        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            if (entry.getValue() != PowerupType.SLOW) continue;
            Point hazard = entry.getKey();
            Vector2 away = new Vector2(currentPos.x - hazard.x, currentPos.y - hazard.y);
            double dist = away.length();
            if (dist > 0 && dist < radiusPx) {
                double strength = (radiusPx - dist) / radiusPx;
                push = push.plus(away.normalized().scaled(strength));
            }
        }
        return push;
    }
}
