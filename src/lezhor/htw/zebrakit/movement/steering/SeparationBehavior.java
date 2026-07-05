package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/** Repels this bot from this player's own other bots when they're within {@code radiusPx}. */
public class SeparationBehavior implements SteeringBehavior {
    private final double radiusPx;

    public SeparationBehavior(double radiusPx) {
        this.radiusPx = radiusPx;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        Vector2 push = Vector2.ZERO;
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            if (i == self.botIndex()) continue;
            Point other = state.botPosition(i);
            Vector2 away = new Vector2(currentPos.x - other.x, currentPos.y - other.y);
            double dist = away.length();
            if (dist > 0 && dist < radiusPx) {
                double strength = (radiusPx - dist) / radiusPx;
                push = push.plus(away.normalized().scaled(strength));
            }
        }
        return push;
    }
}
