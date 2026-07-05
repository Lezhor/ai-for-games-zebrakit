package lezhor.htw.zebrakit.strategy;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.support.GreedyAssignment;
import lezhor.htw.zebrakit.strategy.support.WanderMovement;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Globally assigns bots to reachable powerups (excluding the SLOW hazard) by
 * estimated time-to-reach — distance divided by that bot's own speed, so the
 * fast bot is preferred for a close race and the slow bot isn't sent on a
 * powerup a teammate would clearly reach first. Wanders when unassigned.
 */
public class PowerupHuntStrategy implements Strategy {
    private final Navigator navigator;
    private final WanderMovement wander = new WanderMovement();

    public PowerupHuntStrategy(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        List<Point> candidates = new ArrayList<>();
        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            if (entry.getValue() != PowerupType.SLOW) {
                candidates.add(entry.getKey());
            }
        }

        Point[] positions = new Point[BotRoles.BOT_COUNT];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = state.botPosition(i);
        }

        Map<Integer, Point> assignment = GreedyAssignment.assignLowestCostFirst(BotRoles.BOT_COUNT, candidates,
                (bot, candidate) -> {
                    double dist = navigator.getPathDistance(positions[bot], candidate);
                    return dist >= Double.MAX_VALUE ? Double.MAX_VALUE : dist / bots[bot].speed();
                });

        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            Point target = assignment.get(i);
            result[i] = target != null
                    ? navigator.getNextMoveDirection(state, bots[i], positions[i], target)
                    : wander.next(state, i, positions[i]);
        }
        return result;
    }
}
