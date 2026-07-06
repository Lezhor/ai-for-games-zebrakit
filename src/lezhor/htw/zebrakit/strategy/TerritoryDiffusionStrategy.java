package lezhor.htw.zebrakit.strategy;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.analysis.InfluenceMap;
import lezhor.htw.zebrakit.analysis.OpponentWeights;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.HybridMovementProvider;
import lezhor.htw.zebrakit.movement.MovementProvider;
import lezhor.htw.zebrakit.movement.nav.CachingNavigator;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.movement.steering.SeekPaintValueBehavior;
import lezhor.htw.zebrakit.movement.steering.SeparationBehavior;
import lezhor.htw.zebrakit.movement.steering.SteeringMovementProvider;
import lezhor.htw.zebrakit.movement.steering.WeightedBehavior;
import lezhor.htw.zebrakit.strategy.support.GreedyAssignment;
import lezhor.htw.zebrakit.strategy.support.WanderMovement;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The smart painting agent. One instance coordinates all 3 bots. It picks a
 * <em>general area</em> to paint from a wall-aware {@link InfluenceMap} (value
 * spread by geodesic diffusion, discounted by opponent crowding), then moves
 * there micro-dominantly — local color-seeking drives movement while the area
 * target is a soft restoring leash. Opponent targeting is score-margin aware
 * with a time-shrinking spread (gang the catchable leader, secure your place
 * late). Bots race for bombs and rain, differentiate by their fixed abilities,
 * and never chase the SLOW powerup.
 *
 * All tuning lives in the constants below.
 */
public class TerritoryDiffusionStrategy implements Strategy {

    // --- Opponent targeting (score-margin bump spread; see OpponentWeights) ---
    private static final double SIGMA_MAX = 0.5;   // early: everyone catchable, spread pressure
    private static final double SIGMA_MIN = 0.08;  // late: lock onto our standings-neighbour

    // --- Coarse influence map (area selection) ---
    private static final int INFLUENCE_GRID_SIZE = 64;
    private static final int DIFFUSION_ITERATIONS = 6;
    private static final double DIFFUSION_ALPHA = 0.5;
    private static final double CROWD_STRENGTH = 0.8;      // how hard opponent-dense areas are avoided
    private static final double DISPERSION_SIGMA = 12.0;   // how far our own bots repel each other's areas
    // Per-role distance falloff: bot0 (fast) roams far cheaply, bot2 (slow) prefers nearby.
    private static final double[] DISTANCE_ALPHA = {0.008, 0.035, 0.055};

    // --- Fine local field (steering) ---
    private static final int FINE_WINDOW_RADIUS_CELLS = 5;
    private static final int FINE_CELL_PX = 8;
    private static final int FINE_DIFFUSION_ITERS = 4;
    private static final double FINE_DIFFUSION_ALPHA = 0.5;
    private static final double SEEK_VALUE_WEIGHT = 1.0;
    private static final double SEPARATION_WEIGHT = 0.6;
    private static final double SEPARATION_RADIUS_PX = 60;

    // --- Micro-dominant blend (area target as a soft leash) ---
    private static final double MICRO_WEIGHT = 1.0;
    private static final double AREA_RADIUS_PX = 70;    // within this, wander freely for color (no macro pull)
    private static final double LEASH_RANGE_PX = 220;   // over this beyond the area, full restoring pull
    private static final double MACRO_GAIN = 2.5;

    // --- Area commitment ---
    private static final long TARGET_COMMIT_MS = 1500;   // stay and paint an area at least this long
    private static final double ARRIVAL_RADIUS_PX = 40;
    private static final double AREA_ABANDON_VALUE = 120; // retarget once the area's paint value drops below this

    // --- Path caching ---
    private static final double PATH_DRIFT_THRESHOLD_PX = 60;
    private static final int PATH_RECOMPUTE_TICKS = 20;

    // --- Powerups ---
    private static final double POWERUP_SEARCH_RADIUS_PX = 600;
    private static final double POWERUP_MAX_PATH_DIST = 700;
    private static final double RACE_MARGIN = 1.0;        // go only if our path beats their straight-line by this
    private static final double BOMB_BASE_VALUE = 400;
    private static final double BOMB_TURF_SCALE = 0.5;    // extra bomb value ∝ paint value already at its spot
    private static final double RAIN_VALUE = 500;         // durable (map-wide) — deliberately not undervalued

    private final InfluenceMap influenceMap =
            new InfluenceMap(INFLUENCE_GRID_SIZE, BoardConstants.MAP_SIZE, DIFFUSION_ITERATIONS, DIFFUSION_ALPHA);
    private final MovementProvider territoryMovement;
    private final Navigator powerupNav;
    private final WanderMovement wander = new WanderMovement();
    private final Random rand = new Random();
    private final long matchLengthMs;

    private final Point[] areaTargets = new Point[BotRoles.BOT_COUNT];
    private final long[] areaStartTimes = new long[BotRoles.BOT_COUNT];
    private long matchStartMs;

    // Per-tick scratch, set at the top of decide().
    private int myPlayerNumber;
    private OpponentWeights.Weights weights;

    /**
     * @param territoryNav      color-aware navigator for the painting approach (wrapped in caching + steering)
     * @param powerupNav        plain navigator for powerup routes and race/reachability distances
     * @param matchLengthSeconds assumed match length (drives the time-shrinking targeting spread)
     */
    public TerritoryDiffusionStrategy(Navigator territoryNav, Navigator powerupNav, int matchLengthSeconds) {
        this.powerupNav = powerupNav;
        this.matchLengthMs = matchLengthSeconds * 1000L;

        Navigator cachingMacro = new CachingNavigator(territoryNav, PATH_DRIFT_THRESHOLD_PX, PATH_RECOMPUTE_TICKS);
        SteeringMovementProvider micro = new SteeringMovementProvider(List.of(
                new WeightedBehavior(
                        new SeekPaintValueBehavior(FINE_WINDOW_RADIUS_CELLS, FINE_CELL_PX, FINE_DIFFUSION_ITERS, FINE_DIFFUSION_ALPHA),
                        SEEK_VALUE_WEIGHT),
                new WeightedBehavior(new SeparationBehavior(SEPARATION_RADIUS_PX), SEPARATION_WEIGHT)));
        this.territoryMovement = new HybridMovementProvider(
                cachingMacro, micro, MICRO_WEIGHT, AREA_RADIUS_PX, LEASH_RANGE_PX, MACRO_GAIN);
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        long now = System.currentTimeMillis();
        if (matchStartMs == 0) matchStartMs = now;
        double progress = Math.min(1.0, (now - matchStartMs) / (double) matchLengthMs);
        double sigma = Math.max(SIGMA_MIN, SIGMA_MAX * (1.0 - progress));

        myPlayerNumber = state.myPlayerNumber();
        weights = TerritoryUtils.computeWeights(state, myPlayerNumber, sigma);

        Point[] positions = new Point[BotRoles.BOT_COUNT];
        for (int i = 0; i < positions.length; i++) positions[i] = state.botPosition(i);

        influenceMap.update(state, myPlayerNumber, weights);

        Map<Integer, Point> powerupTargets = assignPowerups(state, bots, positions);

        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            Point powerup = powerupTargets.get(i);
            Point target;
            boolean isPowerup = powerup != null;

            if (isPowerup) {
                target = powerup;
            } else {
                if (needsNewArea(state, positions[i], areaTargets[i], areaStartTimes[i], now)) {
                    areaTargets[i] = influenceMap.bestTargetFor(state, bots[i], positions, areaTargets,
                            DISTANCE_ALPHA[i], DISPERSION_SIGMA, CROWD_STRENGTH, powerupNav, rand);
                    areaStartTimes[i] = now;
                }
                target = areaTargets[i];
            }

            Vector2 dir = Vector2.ZERO;
            if (target != null) {
                MovementProvider movement = isPowerup ? powerupNav : territoryMovement;
                dir = movement.getNextMoveDirection(state, bots[i], positions[i], target);
            }
            // A bot must never freeze (the old agents' worst habit): if we have no target or
            // movement yielded no direction, keep moving by wandering, and drop an area target
            // we couldn't make progress toward so it's re-picked next tick.
            if (dir.isZero()) {
                if (!isPowerup && (target == null || !TerritoryUtils.isNear(positions[i], target, ARRIVAL_RADIUS_PX))) {
                    areaTargets[i] = null;
                }
                dir = wander.next(state, i, positions[i]);
            }
            result[i] = dir;
        }
        return result;
    }

    /** Keep painting an area until the commitment window elapses or the area is worked out — not on mere arrival. */
    private boolean needsNewArea(GameState state, Point pos, Point target, long startTime, long now) {
        if (target == null) return true;
        if (now - startTime > TARGET_COMMIT_MS) return true;
        return TerritoryUtils.cellPaintValue(state, target.x, target.y, myPlayerNumber, weights) < AREA_ABANDON_VALUE;
    }

    /**
     * Greedily assigns bombs/rain we can win to our best-placed bots (lowest
     * time-to-reach per unit value), using real opponent bot positions to judge
     * the race. Never chases SLOW.
     */
    private Map<Integer, Point> assignPowerups(GameState state, BotContext[] bots, Point[] positions) {
        List<Point> winnable = new ArrayList<>();
        List<Point> opponents = state.opponentBotPositions();

        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            PowerupType type = entry.getValue();
            if (type == PowerupType.SLOW) continue;
            Point p = entry.getKey();
            if (!withinReach(positions, p)) continue;
            if (weCanWin(p, positions, opponents)) winnable.add(p);
        }
        if (winnable.isEmpty()) return Map.of();

        return GreedyAssignment.assignLowestCostFirst(BotRoles.BOT_COUNT, winnable, (bot, p) -> {
            double dist = powerupNav.getPathDistance(positions[bot], p);
            if (dist >= Double.MAX_VALUE || dist > POWERUP_MAX_PATH_DIST) return Double.MAX_VALUE;
            double value = powerupValue(state, p);
            double timeToReach = dist / bots[bot].speed();
            return timeToReach / Math.max(1.0, value); // lower = better: fast to reach and/or high value
        });
    }

    private boolean withinReach(Point[] positions, Point p) {
        for (Point pos : positions) {
            if (pos.distance(p) <= POWERUP_SEARCH_RADIUS_PX) return true;
        }
        return false;
    }

    private boolean weCanWin(Point p, Point[] positions, List<Point> opponents) {
        double ourBest = Double.MAX_VALUE;
        for (Point pos : positions) ourBest = Math.min(ourBest, powerupNav.getPathDistance(pos, p));
        if (ourBest >= Double.MAX_VALUE) return false;

        double oppBest = Double.MAX_VALUE;
        for (Point opp : opponents) oppBest = Math.min(oppBest, opp.distance(p));
        // opponents judged by straight line (we can't path them) — conservative, since that underestimates their travel.
        return ourBest <= oppBest * RACE_MARGIN;
    }

    private double powerupValue(GameState state, Point p) {
        PowerupType type = state.activePowerups().get(p);
        if (type == PowerupType.RAIN) return RAIN_VALUE;
        // BOMB: worth more where there's paint value to claim (enemy turf).
        return BOMB_BASE_VALUE + BOMB_TURF_SCALE * TerritoryUtils.cellPaintValue(state, p.x, p.y, myPlayerNumber, weights);
    }
}
