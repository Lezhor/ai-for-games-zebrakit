package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.List;

/**
 * The core of the strategy: from a bot's position, cast {@code DIRECTION_COUNT}
 * evenly-spaced rays, walk each with Bresenham's line algorithm, and sum the
 * evaluators' values along it (weighted by a linear distance falloff — near
 * pixels count more). The best-scoring direction wins; the bot steers straight
 * down it, no pathfinding.
 *
 * <p>Per-bot speed scaling: line length and falloff are scaled by the bot's speed
 * so the fast bot ranges far with a flat falloff and the slow bots stay local.
 * Rays are wall-aware via {@link WallField}: a ray stops at the first wall it
 * hits, and if that wall is closer than {@code MIN_WALL_DISTANCE_PX} the whole
 * direction is disqualified so a bot never commits to driving into a wall.
 */
public final class RayScanner {
    private final BresenhamConfig config;
    private final List<PixelEvaluator> evaluators;
    private final WallField wallField;
    private final double minSpeed;

    public RayScanner(BresenhamConfig config, List<PixelEvaluator> evaluators, WallField wallField) {
        this.config = config;
        this.evaluators = evaluators;
        this.wallField = wallField;
        double min = Double.MAX_VALUE;
        for (double s : BotRoles.SPEED) min = Math.min(min, s);
        this.minSpeed = min;
    }

    /**
     * The best direction to move {@code bot} from {@code pos}, or {@link Vector2#ZERO}
     * if every ray is disqualified (e.g. boxed in by walls) — the caller should then
     * fall back to wandering.
     */
    public Vector2 bestDirection(GameState state, BotContext bot, Point pos) {
        // A bot can spawn on a walkable pixel that still falls inside the *thickened* wall grid. There
        // the paint scan is meaningless (every ray starts blocked), so just head for the nearest open
        // space to get clear of the inflated wall; normal scanning resumes once out.
        if (wallField.isBlocked(pos.x, pos.y)) {
            return escapeDirection(bot, pos);
        }

        double speedScale = speedScale(bot);
        double effLen = config.lineLength() * speedScale;
        double effSlope = config.falloffSlope() / speedScale;

        double best = Double.NEGATIVE_INFINITY;
        Vector2 bestDir = Vector2.ZERO;

        int n = config.directionCount();
        for (int k = 0; k < n; k++) {
            double angle = 2 * Math.PI * k / n;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            int endX = (int) Math.round(pos.x + effLen * dx);
            int endY = (int) Math.round(pos.y + effLen * dy);

            double score = scoreRay(state, bot, pos.x, pos.y, endX, endY, effSlope);
            if (score > best) {
                best = score;
                bestDir = new Vector2(dx, dy);
            }
        }
        return best == Double.NEGATIVE_INFINITY ? Vector2.ZERO : bestDir;
    }

    private double speedScale(BotContext bot) {
        return 1.0 + config.speedScaling() * (bot.speed() / minSpeed - 1.0);
    }

    /**
     * When the bot is inside the (thickened) wall, the direction that reaches open space soonest —
     * i.e. straight out of the wall. Casts the same rays but, instead of scoring paint, measures the
     * distance to each ray's first non-blocked pixel and takes the nearest. {@link Vector2#ZERO} if
     * no direction finds open space within the line length (fully entombed — caller then wanders).
     */
    private Vector2 escapeDirection(BotContext bot, Point pos) {
        double effLen = config.lineLength() * speedScale(bot);
        double bestDist = Double.POSITIVE_INFINITY;
        Vector2 bestDir = Vector2.ZERO;

        int n = config.directionCount();
        for (int k = 0; k < n; k++) {
            double angle = 2 * Math.PI * k / n;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            int endX = (int) Math.round(pos.x + effLen * dx);
            int endY = (int) Math.round(pos.y + effLen * dy);

            double dist = firstFreeDistance(pos.x, pos.y, endX, endY);
            if (dist < bestDist) {
                bestDist = dist;
                bestDir = new Vector2(dx, dy);
            }
        }
        return bestDir;
    }

    /** Distance from (x0,y0) to the first non-blocked pixel along the Bresenham line, or +inf if none. */
    private double firstFreeDistance(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            if (!wallField.isBlocked(x, y)) return Math.hypot(x - x0, y - y0);
            if (x == x1 && y == y1) return Double.POSITIVE_INFINITY;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    /**
     * Walks the Bresenham line from (x0,y0) to (x1,y1), summing evaluator values × falloff.
     * Leading wall pixels (the bot may sit inside the thickened margin) are skipped; once the
     * ray has entered free space, the first wall pixel stops it. Returns {@code -inf} when the
     * ray never leaves a wall or hits one closer than the min wall distance.
     */
    private double scoreRay(GameState state, BotContext bot, int x0, int y0, int x1, int y1, double effSlope) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;

        boolean hasLeftWall = false;
        double wallHitDist = Double.POSITIVE_INFINITY;
        double total = 0;
        double weightSum = 0; // Σ falloff over scored pixels — divide by this for the per-pixel average

        while (true) {
            double d = Math.hypot(x - x0, y - y0);
            boolean blocked = wallField.isBlocked(x, y);

            if (!hasLeftWall) {
                if (blocked) {
                    if (x == x1 && y == y1) break;
                    // advance without scoring; still inside the initial wall margin
                    int e2 = 2 * err;
                    if (e2 > -dy) { err -= dy; x += sx; }
                    if (e2 < dx) { err += dx; y += sy; }
                    continue;
                }
                hasLeftWall = true;
            } else if (blocked) {
                wallHitDist = d;
                break;
            }

            double falloff = 1.0 - effSlope * d;
            if (falloff <= 0) break; // falloff exhausted — remaining pixels add nothing (not a wall hit)

            for (PixelEvaluator evaluator : evaluators) {
                total += evaluator.weight() * evaluator.valueAt(x, y, state, bot) * falloff;
            }
            weightSum += falloff;

            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }

        if (!hasLeftWall) return Double.NEGATIVE_INFINITY;        // boxed in along this ray
        if (wallHitDist < config.minWallDistancePx()) return Double.NEGATIVE_INFINITY;
        // Per-pixel average, not the raw sum, so a ray cut short by a wall isn't penalised for being
        // short — only judged on what its pixels are worth. No default/clamp here: a low average is a
        // real, comparable score, so the best of several poor directions still wins. weightSum is > 0
        // here in practice — a bot can't sit deeper than the thin wall margin, so the ray always exits
        // it at a small distance where falloff is still positive and at least one pixel is scored.
        return total / weightSum;
    }
}
