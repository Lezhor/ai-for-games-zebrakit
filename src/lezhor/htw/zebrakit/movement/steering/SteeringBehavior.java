package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * One composable steering contribution. The returned vector is not
 * necessarily unit length — its magnitude conveys how urgently this behavior
 * wants to pull movement in that direction this tick, which
 * {@link SteeringMovementProvider} combines with a weight.
 */
public interface SteeringBehavior {
    Vector2 steer(GameState state, BotContext self, Point currentPos, Point target);
}
