package lezhor.htw.zebrakit.strategy;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.analysis.InfluenceMap;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.MovementProvider;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.support.WanderMovement;

import java.awt.Point;
import java.util.Map;
import java.util.Random;

/**
 * Paints territory using {@link InfluenceMap} to pick valuable targets,
 * detouring for nearby powerups first. `territoryMovement` drives the final
 * approach to a painting target (e.g. color-aware pathfinding, steering, or a
 * hybrid); `powerupMovement` is a plain {@link Navigator} used for
 * path-distance-based decisions (powerup racing, reachability checks) and for
 * the fastest-route leg when actually fetching a powerup.
 *
 * Configuring both movement roles with the same plain geometric navigator
 * reproduces the old ConvolutionAgent's behavior exactly — it's a
 * configuration of this class, not a separate one.
 */
public class TerritoryPaintStrategy implements Strategy {
    private static final int INFLUENCE_GRID_SIZE = 64;
    private static final int INFLUENCE_SMOOTHING_RADIUS_PX = 64;
    private static final long TARGET_REFRESH_MS = 2000;
    private static final double ARRIVAL_RADIUS_PX = 20;
    private static final int PAINTED_THRESHOLD = 220;
    private static final double POWERUP_MATCH_RADIUS_PX = 15;
    private static final double POWERUP_SEARCH_RADIUS_PX = 500;
    private static final double POWERUP_MAX_PATH_DIST = 600;
    private static final double[] ALPHA = {0.01, 0.03, 0.06};
    private static final double DISPERSION_SIGMA = 12.0;

    private final MovementProvider territoryMovement;
    private final Navigator powerupMovement;
    private final InfluenceMap influenceMap =
            new InfluenceMap(INFLUENCE_GRID_SIZE, BoardConstants.MAP_SIZE, INFLUENCE_SMOOTHING_RADIUS_PX);
    private final WanderMovement wander = new WanderMovement();
    private final Random rand = new Random();

    private final Point[] targets = new Point[BotRoles.BOT_COUNT];
    private final long[] targetStartTimes = new long[BotRoles.BOT_COUNT];
    private boolean influenceMapUpdatedThisTick;

    public TerritoryPaintStrategy(MovementProvider territoryMovement, Navigator powerupMovement) {
        this.territoryMovement = territoryMovement;
        this.powerupMovement = powerupMovement;
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        influenceMapUpdatedThisTick = false;
        int myPlayerNumber = state.myPlayerNumber();

        Point[] positions = new Point[BotRoles.BOT_COUNT];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = state.botPosition(i);
        }

        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            if (needsNewTarget(state, positions[i], targets[i], targetStartTimes[i], myPlayerNumber)) {
                Point powerupTarget = findBestPowerup(state, i, positions);
                if (powerupTarget != null) {
                    targets[i] = powerupTarget;
                } else {
                    if (!influenceMapUpdatedThisTick) {
                        influenceMap.update(state, myPlayerNumber);
                        influenceMapUpdatedThisTick = true;
                    }
                    targets[i] = influenceMap.bestTargetFor(state, bots[i], positions, targets,
                            ALPHA[i], DISPERSION_SIGMA, powerupMovement, rand);
                }
                targetStartTimes[i] = System.currentTimeMillis();
            }

            Vector2 dir = Vector2.ZERO;
            if (targets[i] != null) {
                MovementProvider movement = isPowerupTarget(state, targets[i]) ? powerupMovement : territoryMovement;
                dir = movement.getNextMoveDirection(state, bots[i], positions[i], targets[i]);
                if (dir.isZero() && !TerritoryUtils.isNear(positions[i], targets[i], ARRIVAL_RADIUS_PX)) {
                    targets[i] = null; // unreachable
                }
            }
            result[i] = targets[i] != null ? dir : wander.next(state, i, positions[i]);
        }
        return result;
    }

    private boolean needsNewTarget(GameState state, Point pos, Point target, long targetStartTime, int myPlayerNumber) {
        if (target == null) return true;
        if (TerritoryUtils.isNear(pos, target, ARRIVAL_RADIUS_PX)) return true;
        if (System.currentTimeMillis() - targetStartTime > TARGET_REFRESH_MS) return true;
        return TerritoryUtils.isPainted(state, target, myPlayerNumber, PAINTED_THRESHOLD);
    }

    private boolean isPowerupTarget(GameState state, Point target) {
        return state.activePowerups().keySet().stream().anyMatch(p -> p.distance(target) < POWERUP_MATCH_RADIUS_PX);
    }

    private Point findBestPowerup(GameState state, int botIndex, Point[] positions) {
        Point best = null;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            if (entry.getValue() == PowerupType.SLOW) continue;
            Point p = entry.getKey();
            if (p.distance(positions[botIndex]) > POWERUP_SEARCH_RADIUS_PX) continue;

            double dist = powerupMovement.getPathDistance(positions[botIndex], p);
            if (dist < bestDist) {
                boolean someoneCloser = false;
                for (int other = 0; other < BotRoles.BOT_COUNT; other++) {
                    if (other == botIndex) continue;
                    if (powerupMovement.getPathDistance(positions[other], p) < dist) {
                        someoneCloser = true;
                        break;
                    }
                }
                if (!someoneCloser) {
                    bestDist = dist;
                    best = p;
                }
            }
        }

        return bestDist < POWERUP_MAX_PATH_DIST ? best : null;
    }
}
