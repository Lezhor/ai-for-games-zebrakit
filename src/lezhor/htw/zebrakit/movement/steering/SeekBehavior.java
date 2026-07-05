package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/** Steers straight toward the target, full strength regardless of distance. */
public class SeekBehavior implements SteeringBehavior {
    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        return Vector2.towards(currentPos, target).normalized();
    }
}
