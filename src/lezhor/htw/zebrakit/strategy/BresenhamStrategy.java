package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.bresenham.BotDistanceEvaluator;
import lezhor.htw.zebrakit.strategy.bresenham.BresenhamConfig;
import lezhor.htw.zebrakit.strategy.bresenham.LeaderWeighting;
import lezhor.htw.zebrakit.strategy.bresenham.PaintValueEvaluator;
import lezhor.htw.zebrakit.strategy.bresenham.PixelEvaluator;
import lezhor.htw.zebrakit.strategy.bresenham.PlayerWeights;
import lezhor.htw.zebrakit.strategy.bresenham.PowerupOverride;
import lezhor.htw.zebrakit.strategy.bresenham.RayScanner;
import lezhor.htw.zebrakit.strategy.bresenham.WallField;
import lezhor.htw.zebrakit.strategy.support.WanderMovement;

import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A deliberately simple painting agent with no pathfinding (except for powerup
 * chases). Each bot decides where to go by casting rays in
 * {@code DIRECTION_COUNT} directions and walking each with Bresenham's line
 * algorithm, scoring the pixels along it: our own colour counts against a
 * direction (no headroom to gain there), enemy colour counts for it (points to
 * strip), and near pixels count more than far via a linear falloff. The bot
 * moves straight down the best-scoring ray.
 *
 * <p>All the sensing is delegated to a list of {@link PixelEvaluator}s (v1 has
 * one — {@link PaintValueEvaluator}); walls are a separate {@link WallField} the
 * scanner uses to stop rays and keep bots off walls. Per-opponent "leader"
 * weights are recomputed each tick from live scores and injected into the
 * evaluators. Tuning lives in an optional {@code config.ini} (see
 * {@link BresenhamConfig}); with no config every value falls back to its default.
 */
public class BresenhamStrategy implements Strategy {

    private final BresenhamConfig config;
    private final Navigator powerupNav;
    private final PowerupOverride powerupOverride;
    private final List<PixelEvaluator> evaluators;
    private final WallField wallField;
    private final RayScanner rayScanner;
    private final WanderMovement wander = new WanderMovement();

    // Per-bot committed heading and how many ticks until we re-cast the rays.
    private final Vector2[] committed = new Vector2[BotRoles.BOT_COUNT];
    private final int[] ticksUntilRecheck = new int[BotRoles.BOT_COUNT];
    private boolean initialized;

    public BresenhamStrategy(Navigator powerupNav, BresenhamConfig config) {
        this.config = config;
        this.powerupNav = powerupNav;
        this.powerupOverride = new PowerupOverride(powerupNav, config);
        this.wallField = new WallField(config.wallGridScale(), config.wallRadiusPx());
        this.evaluators = List.of(new PaintValueEvaluator(), new BotDistanceEvaluator());
        this.rayScanner = new RayScanner(config, evaluators, wallField);
        Arrays.fill(committed, Vector2.ZERO);
    }

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        lazyInit(state);

        int myPlayerNumber = state.myPlayerNumber();
        PlayerWeights weights = LeaderWeighting.compute(state, myPlayerNumber, config);
        for (PixelEvaluator evaluator : evaluators) {
            evaluator.update(state, myPlayerNumber, weights);
        }

        Point[] positions = new Point[BotRoles.BOT_COUNT];
        for (int i = 0; i < positions.length; i++) positions[i] = state.botPosition(i);

        Map<Integer, Point> powerupTargets = powerupOverride.assign(state, bots, positions);

        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            Point powerup = powerupTargets.get(i);
            Vector2 dir;
            if (powerup != null) {
                dir = powerupNav.getNextMoveDirection(state, bots[i], positions[i], powerup);
                committed[i] = Vector2.ZERO; // recompute a fresh heading once the chase ends
                ticksUntilRecheck[i] = 0;
            } else {
                dir = paintDirection(state, bots[i], positions[i], i);
            }
            if (dir.isZero()) dir = wander.next(state, i, positions[i]); // never freeze
            result[i] = dir;
        }
        return result;
    }

    /**
     * The committed ray heading, recast either on the periodic interval or — every tick, as a
     * precaution — when the current heading would drive the bot into a wall within the safety
     * margin before the next scheduled recheck.
     */
    private Vector2 paintDirection(GameState state, BotContext bot, Point pos, int i) {
        if (ticksUntilRecheck[i] <= 0 || committed[i].isZero() || aboutToHitWall(pos, committed[i])) {
            committed[i] = rayScanner.bestDirection(state, bot, pos);
            ticksUntilRecheck[i] = config.recheckIntervalTicks();
        } else {
            ticksUntilRecheck[i]--;
        }
        return committed[i];
    }

    private boolean aboutToHitWall(Point pos, Vector2 dir) {
        if (dir.isZero()) return false;
        int x = pos.x + (int) Math.round(dir.x() * config.minWallDistancePx());
        int y = pos.y + (int) Math.round(dir.y() * config.minWallDistancePx());
        return wallField.isBlocked(x, y);
    }

    private void lazyInit(GameState state) {
        if (initialized) return;
        wallField.init(state);
        for (PixelEvaluator evaluator : evaluators) evaluator.init(state, config);
        initialized = true;
    }
}
