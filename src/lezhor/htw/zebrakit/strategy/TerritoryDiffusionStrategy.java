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
import lezhor.htw.zebrakit.movement.steering.WallAvoidanceBehavior;
import lezhor.htw.zebrakit.movement.steering.WeightedBehavior;
import lezhor.htw.zebrakit.strategy.support.GreedyAssignment;
import lezhor.htw.zebrakit.strategy.support.StrategyConfig;
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
 * All tuning constants below are also overridable via an optional
 * {@code config.ini} ({@code --config <path>}); when none is given, every
 * value defaults to the literal shown here.
 */
public class TerritoryDiffusionStrategy implements Strategy {

    // Fixed fact about the game loop (GameLoop/Main hardcode a 10ms tick sleep) — not a tuning
    // knob, just what "degrees per second" needs to be converted into "radians per tick" against.
    private static final double ASSUMED_TICK_HZ = 100;

    private final double sigmaMax;   // early: everyone catchable, spread pressure
    private final double sigmaMin;   // late: lock onto our standings-neighbour

    private final int influenceGridSize;
    private final int diffusionIterations;
    private final double diffusionAlpha;
    private final double crowdStrength;      // how hard opponent-dense areas are avoided
    private final double dispersionSigma;    // how far our own bots repel each other's areas
    // Per-role distance falloff: bot0 (fast) roams far cheaply, bot2 (slow) prefers nearby.
    private final double[] distanceAlpha;

    private final int fineWindowRadiusCells;
    private final int fineCellPx;
    private final int fineDiffusionIters;
    private final double fineDiffusionAlpha;
    private final double seekValueWeight;
    private final double separationWeight;
    private final double separationRadiusPx;
    // Local target-picking ramps cells closer than localMinRadiusMultiplier * paintRadius down
    // toward zero weight (linearly, not a hard cutoff — a hard ring boundary is itself orbitable)
    // — a cell nearly under the bot barely offsets the value-weighted centroid, so its value noise
    // can otherwise flip the resulting direction's sign tick-to-tick. 0 disables the ramp.
    private final double localMinRadiusMultiplier;
    // See TerritoryUtils#cellPaintValue: zero on an already-white cell, largest on a fully
    // enemy-owned one, so this is the knob for how much harder enemy territory is favored over white.
    private final double ownGainWeight;
    // Directional-consistency bonus (see LocalPaintField#bestDirection): favors cells roughly in
    // the same direction as last tick's local pick, so the local target doesn't 180 every tick
    // chasing whichever side of an already-painted patch still has the most remaining value.
    private final double localPersistenceStrength;

    private final double microWeight;
    private final double areaRadiusPx;    // within this, wander freely for color (no macro pull)
    private final double leashRangePx;    // over this beyond the area, full restoring pull
    private final double macroGain;

    // Wall avoidance is applied to EVERY bot's final direction: territory, powerup, wander.
    private final double wallAvoidRadiusPx;  // keep bot centers at least ~this far from walls
    private final int wallAvoidRayCount;
    private final int wallAvoidStepPx;
    private final double wallAvoidWeight;    // high: overrides value-seeking/path-following near walls
    // How long the turn clamp stays disabled after a wall push last fired, even once it reads
    // zero again — prevents the clamp from re-engaging mid-escape and sawtoothing at the wall edge.
    private final long wallEscapeCooldownMs;

    // Turn-rate limiting is a secondary safeguard against direction flip-flop (belt-and-suspenders
    // to the min-radius exclusion above). Per-bot-role, in degrees/second; 0 disables the clamp for
    // that bot. Bypassed entirely when a wall is near (bot may spin as sharply as needed to escape)
    // or when chasing a powerup (a deliberate, often urgent retarget).
    private final double[] maxTurnDegPerSec;

    private final long targetCommitMs;   // stay and paint an area at least this long
    private final double arrivalRadiusPx;
    private final double areaAbandonValue; // retarget once the area's paint value drops below this

    private final double pathDriftThresholdPx;
    private final int pathRecomputeTicks;

    private final double powerupSearchRadiusPx;
    private final double powerupMaxPathDist;
    private final double raceMargin;        // go only if our path beats their straight-line by this
    private final double bombBaseValue;
    private final double bombTurfScale;    // extra bomb value ∝ paint value already at its spot
    private final double rainValue;         // durable (map-wide) — deliberately not undervalued

    private final InfluenceMap influenceMap;
    private final MovementProvider territoryMovement;
    private final Navigator powerupNav;
    private final WallAvoidanceBehavior wallAvoidance;
    private final WanderMovement wander = new WanderMovement();
    private final Random rand = new Random();
    private final long matchLengthMs;

    private final Point[] areaTargets = new Point[BotRoles.BOT_COUNT];
    private final long[] areaStartTimes = new long[BotRoles.BOT_COUNT];
    private final Vector2[] lastDirection = new Vector2[BotRoles.BOT_COUNT];
    private final long[] wallEscapeUntil = new long[BotRoles.BOT_COUNT];
    private long matchStartMs;

    // Per-tick scratch, set at the top of decide().
    private int myPlayerNumber;
    private OpponentWeights.Weights weights;

    /**
     * @param territoryNav       color-aware navigator for the painting approach (wrapped in caching + steering)
     * @param powerupNav         plain navigator for powerup routes and race/reachability distances
     * @param matchLengthSeconds assumed match length (drives the time-shrinking targeting spread)
     * @param config             optional tuning override (see class doc); {@code null} uses defaults
     */
    public TerritoryDiffusionStrategy(Navigator territoryNav, Navigator powerupNav, int matchLengthSeconds,
                                       StrategyConfig config) {
        if (config != null) config.requireType("TerritoryDiffusion");

        this.sigmaMax = cfgDouble(config, "SIGMA_MAX", 0.1);
        this.sigmaMin = cfgDouble(config, "SIGMA_MIN", 0.04);

        this.influenceGridSize = cfgInt(config, "INFLUENCE_GRID_SIZE", 64);
        this.diffusionIterations = cfgInt(config, "DIFFUSION_ITERATIONS", 5);
        this.diffusionAlpha = cfgDouble(config, "DIFFUSION_ALPHA", 0.8);
        this.crowdStrength = cfgDouble(config, "CROWD_STRENGTH", 0.8);
        this.dispersionSigma = cfgDouble(config, "DISPERSION_SIGMA", 12.0);
        this.distanceAlpha = cfgDoubleArray(config, "DISTANCE_ALPHA", new double[]{0.008, 0.035, 0.055});

        // 10 cells * 20px = 200px local look-around radius.
        this.fineWindowRadiusCells = cfgInt(config, "FINE_WINDOW_RADIUS_CELLS", 10);
        this.fineCellPx = cfgInt(config, "FINE_CELL_PX", 20);
        this.fineDiffusionIters = cfgInt(config, "FINE_DIFFUSION_ITERS", 3);
        this.fineDiffusionAlpha = cfgDouble(config, "FINE_DIFFUSION_ALPHA", 0.6);
        this.seekValueWeight = cfgDouble(config, "SEEK_VALUE_WEIGHT", 1.2);
        this.separationWeight = cfgDouble(config, "SEPARATION_WEIGHT", 0.4);
        this.separationRadiusPx = cfgDouble(config, "SEPARATION_RADIUS_PX", 100);
        this.localMinRadiusMultiplier = cfgDouble(config, "LOCAL_MIN_RADIUS_MULTIPLIER", 2.0);
        this.ownGainWeight = cfgDouble(config, "OWN_GAIN_WEIGHT", 4.0);
        this.localPersistenceStrength = cfgDouble(config, "LOCAL_PERSISTENCE_STRENGTH", 2.0);

        this.microWeight = cfgDouble(config, "MICRO_WEIGHT", 1.0);
        // Widened + gentler: the coarse area target should barely matter next to local movement —
        // zero pull for a much larger radius around the area, and even beyond that a softer pull.
        this.areaRadiusPx = cfgDouble(config, "AREA_RADIUS_PX", 150);
        this.leashRangePx = cfgDouble(config, "LEASH_RANGE_PX", 350);
        this.macroGain = cfgDouble(config, "MACRO_GAIN", 1.2);

        this.wallAvoidRadiusPx = cfgDouble(config, "WALL_AVOID_RADIUS_PX", 48);
        this.wallAvoidRayCount = cfgInt(config, "WALL_AVOID_RAY_COUNT", 12);
        this.wallAvoidStepPx = cfgInt(config, "WALL_AVOID_STEP_PX", 6);
        this.wallAvoidWeight = cfgDouble(config, "WALL_AVOID_WEIGHT", 4.0);
        this.wallEscapeCooldownMs = cfgLong(config, "WALL_ESCAPE_COOLDOWN_MS", 250);

        this.maxTurnDegPerSec = cfgDoubleArray(config, "MAX_TURN_DEG_PER_SEC", new double[]{150, 360, 200});

        this.targetCommitMs = cfgLong(config, "TARGET_COMMIT_MS", 300);
        this.arrivalRadiusPx = cfgDouble(config, "ARRIVAL_RADIUS_PX", 40);
        this.areaAbandonValue = cfgDouble(config, "AREA_ABANDON_VALUE", 300);

        this.pathDriftThresholdPx = cfgDouble(config, "PATH_DRIFT_THRESHOLD_PX", 60);
        this.pathRecomputeTicks = cfgInt(config, "PATH_RECOMPUTE_TICKS", 20);

        this.powerupSearchRadiusPx = cfgDouble(config, "POWERUP_SEARCH_RADIUS_PX", 700);
        this.powerupMaxPathDist = cfgDouble(config, "POWERUP_MAX_PATH_DIST", 700);
        this.raceMargin = cfgDouble(config, "RACE_MARGIN", 1.0);
        this.bombBaseValue = cfgDouble(config, "BOMB_BASE_VALUE", 400);
        this.bombTurfScale = cfgDouble(config, "BOMB_TURF_SCALE", 0.5);
        this.rainValue = cfgDouble(config, "RAIN_VALUE", 500);

        this.influenceMap = new InfluenceMap(influenceGridSize, BoardConstants.MAP_SIZE, diffusionIterations, diffusionAlpha);
        this.wallAvoidance = new WallAvoidanceBehavior(wallAvoidRadiusPx, wallAvoidRayCount, wallAvoidStepPx);

        this.powerupNav = powerupNav;
        this.matchLengthMs = matchLengthSeconds * 1000L;

        Navigator cachingMacro = new CachingNavigator(territoryNav, pathDriftThresholdPx, pathRecomputeTicks);
        SteeringMovementProvider micro = new SteeringMovementProvider(List.of(
                new WeightedBehavior(
                        new SeekPaintValueBehavior(fineWindowRadiusCells, fineCellPx, fineDiffusionIters, fineDiffusionAlpha,
                                localMinRadiusMultiplier, ownGainWeight, localPersistenceStrength),
                        seekValueWeight),
                new WeightedBehavior(new SeparationBehavior(separationRadiusPx), separationWeight)));
        this.territoryMovement = new HybridMovementProvider(
                cachingMacro, micro, microWeight, areaRadiusPx, leashRangePx, macroGain);
    }

    private static double cfgDouble(StrategyConfig config, String key, double fallback) {
        return config == null ? fallback : config.getDouble(key, fallback);
    }

    private static int cfgInt(StrategyConfig config, String key, int fallback) {
        return config == null ? fallback : config.getInt(key, fallback);
    }

    private static long cfgLong(StrategyConfig config, String key, long fallback) {
        return config == null ? fallback : config.getLong(key, fallback);
    }

    private static double[] cfgDoubleArray(StrategyConfig config, String key, double[] fallback) {
        return config == null ? fallback : config.getDoubleArray(key, fallback);
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        long now = System.currentTimeMillis();
        if (matchStartMs == 0) matchStartMs = now;
        double progress = Math.min(1.0, (now - matchStartMs) / (double) matchLengthMs);
        double sigma = Math.max(sigmaMin, sigmaMax * (1.0 - progress));

        myPlayerNumber = state.myPlayerNumber();
        weights = TerritoryUtils.computeWeights(state, myPlayerNumber, sigma);

        Point[] positions = new Point[BotRoles.BOT_COUNT];
        for (int i = 0; i < positions.length; i++) positions[i] = state.botPosition(i);

        influenceMap.update(state, myPlayerNumber, weights, ownGainWeight);

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
                            distanceAlpha[i], dispersionSigma, crowdStrength, powerupNav, rand);
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
                if (!isPowerup && (target == null || !TerritoryUtils.isNear(positions[i], target, arrivalRadiusPx))) {
                    areaTargets[i] = null;
                }
                dir = wander.next(state, i, positions[i]);
            }

            dir = applyWallAvoidanceAndTurnLimit(state, bots[i], positions[i], dir, isPowerup, i, now);
            lastDirection[i] = dir;
            result[i] = dir;
        }
        return result;
    }

    /**
     * Wall avoidance always wins when a wall is near (bot may spin as sharply as it needs to
     * escape), and stays in control for a short cooldown afterward even once the push reads zero
     * again — without this grace period, the turn clamp re-engages the instant the bot drifts
     * back outside {@code wallAvoidRadiusPx}, dragging it slowly back toward whatever still wants
     * to go through the wall, which re-triggers the escape a moment later: a sawtooth right at the
     * wall edge that reads as wiggling. Otherwise, unless chasing a powerup, the direction is
     * turn-rate limited relative to last tick's direction to stop flip-flop noise from producing
     * visible jitter.
     */
    private Vector2 applyWallAvoidanceAndTurnLimit(GameState state, BotContext self, Point pos, Vector2 dir,
                                                    boolean isPowerup, int botIndex, long now) {
        Vector2 wallPush = wallAvoidance.steer(state, self, pos, pos);
        if (!wallPush.isZero()) {
            wallEscapeUntil[botIndex] = now + wallEscapeCooldownMs;
            return dir.plus(wallPush.scaled(wallAvoidWeight)).normalized();
        }
        if (isPowerup || now < wallEscapeUntil[botIndex]) return dir;

        double maxRadPerTick = Math.toRadians(maxTurnDegPerSec[botIndex]) / ASSUMED_TICK_HZ;
        return clampTurn(lastDirection[botIndex], dir, maxRadPerTick);
    }

    /** Rotates {@code prev} toward {@code target} by at most {@code maxRadPerTick}. 0 disables the clamp. */
    private static Vector2 clampTurn(Vector2 prev, Vector2 target, double maxRadPerTick) {
        if (maxRadPerTick <= 0 || prev == null || prev.isZero() || target.isZero()) return target;

        double prevAngle = Math.atan2(prev.y(), prev.x());
        double targetAngle = Math.atan2(target.y(), target.x());
        double delta = targetAngle - prevAngle;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;

        if (Math.abs(delta) <= maxRadPerTick) return target;
        double clamped = prevAngle + Math.copySign(maxRadPerTick, delta);
        return new Vector2(Math.cos(clamped), Math.sin(clamped));
    }

    /** Keep painting an area until the commitment window elapses or the area is worked out — not on mere arrival. */
    private boolean needsNewArea(GameState state, Point pos, Point target, long startTime, long now) {
        if (target == null) return true;
        if (now - startTime > targetCommitMs) return true;
        return TerritoryUtils.cellPaintValue(state, target.x, target.y, myPlayerNumber, weights, ownGainWeight) < areaAbandonValue;
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
            if (dist >= Double.MAX_VALUE || dist > powerupMaxPathDist) return Double.MAX_VALUE;
            double value = powerupValue(state, p);
            double timeToReach = dist / bots[bot].speed();
            return timeToReach / Math.max(1.0, value); // lower = better: fast to reach and/or high value
        });
    }

    private boolean withinReach(Point[] positions, Point p) {
        for (Point pos : positions) {
            if (pos.distance(p) <= powerupSearchRadiusPx) return true;
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
        return ourBest <= oppBest * raceMargin;
    }

    private double powerupValue(GameState state, Point p) {
        PowerupType type = state.activePowerups().get(p);
        if (type == PowerupType.RAIN) return rainValue;
        // BOMB: worth more where there's paint value to claim (enemy turf).
        return bombBaseValue + bombTurfScale * TerritoryUtils.cellPaintValue(state, p.x, p.y, myPlayerNumber, weights, ownGainWeight);
    }
}
