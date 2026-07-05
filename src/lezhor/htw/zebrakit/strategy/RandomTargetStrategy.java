package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.MovementProvider;

import java.awt.Point;
import java.util.Random;

/** Sends each bot to a random walkable point, picking a new one on arrival, on a periodic refresh, or if unreachable. */
public class RandomTargetStrategy implements Strategy {
    private static final long RETARGET_INTERVAL_MS = 10_000;
    private static final double ARRIVAL_RADIUS_PX = 20;

    private final MovementProvider movement;
    private final Random rand = new Random();
    private final Point[] targets = new Point[BotRoles.BOT_COUNT];
    private long lastRetargetTime = 0;

    public RandomTargetStrategy(MovementProvider movement) {
        this.movement = movement;
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];

        long now = System.currentTimeMillis();
        boolean timeToSwitch = now - lastRetargetTime > RETARGET_INTERVAL_MS;
        if (timeToSwitch) lastRetargetTime = now;

        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            Point pos = state.botPosition(i);
            if (timeToSwitch || targets[i] == null || TerritoryUtils.isNear(pos, targets[i], ARRIVAL_RADIUS_PX)) {
                targets[i] = randomWalkableTarget(state);
            }

            Vector2 dir = movement.getNextMoveDirection(state, bots[i], pos, targets[i]);
            if (dir.isZero() && !TerritoryUtils.isNear(pos, targets[i], ARRIVAL_RADIUS_PX)) {
                // Unreachable (disconnected island) — pick a different target.
                targets[i] = randomWalkableTarget(state);
                dir = movement.getNextMoveDirection(state, bots[i], pos, targets[i]);
            }
            result[i] = dir;
        }
        return result;
    }

    private Point randomWalkableTarget(GameState state) {
        while (true) {
            int x = rand.nextInt(BoardConstants.MAP_SIZE);
            int y = rand.nextInt(BoardConstants.MAP_SIZE);
            if (state.isWalkable(x, y)) return new Point(x, y);
        }
    }
}
