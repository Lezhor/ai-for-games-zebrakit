package lezhor.htw.zebrakit.strategy.support;

import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.Random;

/**
 * Bounces bots off walls in a straight line, switching to a new random
 * direction on collision. Shared fallback movement for any strategy that
 * doesn't currently have a better target for a bot.
 */
public class WanderMovement {
    private static final int LOOKAHEAD_MIN_PX = 5;
    private static final int LOOKAHEAD_MAX_PX = 25;
    private static final int LOOKAHEAD_STEP_PX = 5;

    private final Random rand = new Random();
    private final Vector2[] directions = new Vector2[BotRoles.BOT_COUNT];

    public WanderMovement() {
        for (int i = 0; i < directions.length; i++) {
            directions[i] = randomDirection();
        }
    }

    public Vector2 next(GameState state, int botIndex, Point pos) {
        if (willHitWall(state, pos, directions[botIndex])) {
            directions[botIndex] = randomDirection();
        }
        return directions[botIndex];
    }

    private boolean willHitWall(GameState state, Point pos, Vector2 dir) {
        for (int step = LOOKAHEAD_MIN_PX; step <= LOOKAHEAD_MAX_PX; step += LOOKAHEAD_STEP_PX) {
            int checkX = pos.x + (int) (dir.x() * step);
            int checkY = pos.y + (int) (dir.y() * step);
            if (!state.isWalkable(checkX, checkY)) return true;
        }
        return false;
    }

    private Vector2 randomDirection() {
        return new Vector2(rand.nextFloat() * 2 - 1, rand.nextFloat() * 2 - 1).normalized();
    }
}
