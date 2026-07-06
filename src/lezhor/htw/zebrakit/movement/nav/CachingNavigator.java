package lezhor.htw.zebrakit.movement.nav;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.List;

/**
 * Wraps a {@link Navigator} and reuses each bot's computed path instead of
 * re-pathing every tick. The cached path is a <em>guide, not a rail</em>: since
 * local steering drifts a bot off the exact line to grab paint value, each tick
 * we re-anchor to the nearest waypoint still ahead rather than following by
 * index. A full recompute happens only when the target changes, the bot has
 * drifted past {@code driftThresholdPx}, or every {@code recomputeEveryTicks}.
 *
 * {@code findPath}/{@code getPathDistance} pass straight through (uncached) —
 * they're used for one-off race/reachability decisions, not per-tick movement.
 */
public class CachingNavigator implements Navigator {
    private final Navigator delegate;
    private final double driftThresholdPx;
    private final int recomputeEveryTicks;

    private final List<Point>[] paths;
    private final Point[] pathTargets;
    private final int[] ticksSinceRecompute;

    @SuppressWarnings("unchecked")
    public CachingNavigator(Navigator delegate, double driftThresholdPx, int recomputeEveryTicks) {
        this.delegate = delegate;
        this.driftThresholdPx = driftThresholdPx;
        this.recomputeEveryTicks = recomputeEveryTicks;
        this.paths = new List[BotRoles.BOT_COUNT];
        this.pathTargets = new Point[BotRoles.BOT_COUNT];
        this.ticksSinceRecompute = new int[BotRoles.BOT_COUNT];
    }

    @Override
    public void initialize(long seed) {
        delegate.initialize(seed);
    }

    @Override
    public void initialize(NetworkClient client) {
        delegate.initialize(client);
    }

    @Override
    public List<Point> findPath(Point start, Point end) {
        return delegate.findPath(start, end);
    }

    @Override
    public double getPathDistance(Point start, Point end) {
        return delegate.getPathDistance(start, end);
    }

    @Override
    public Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos) {
        int i = self.botIndex();

        boolean recompute = paths[i] == null
                || pathTargets[i] == null
                || !pathTargets[i].equals(targetPos)
                || ticksSinceRecompute[i] >= recomputeEveryTicks
                || distanceToPath(paths[i], currentPos) > driftThresholdPx;

        if (recompute) {
            paths[i] = delegate.findPath(currentPos, targetPos);
            pathTargets[i] = new Point(targetPos);
            ticksSinceRecompute[i] = 0;
        } else {
            ticksSinceRecompute[i]++;
        }

        List<Point> path = paths[i];
        if (path == null || path.size() < 2) return Vector2.ZERO;

        int aim = Math.min(closestIndex(path, currentPos) + 1, path.size() - 1);
        for (int k = aim; k < path.size(); k++) {
            Vector2 dir = Vector2.towards(currentPos, path.get(k));
            if (!dir.isZero()) return dir.normalized();
        }
        return Vector2.ZERO;
    }

    private int closestIndex(List<Point> path, Point pos) {
        int best = 0;
        double bestSq = Double.MAX_VALUE;
        for (int k = 0; k < path.size(); k++) {
            double d = path.get(k).distanceSq(pos);
            if (d < bestSq) {
                bestSq = d;
                best = k;
            }
        }
        return best;
    }

    private double distanceToPath(List<Point> path, Point pos) {
        double bestSq = Double.MAX_VALUE;
        for (Point p : path) {
            bestSq = Math.min(bestSq, p.distanceSq(pos));
        }
        return Math.sqrt(bestSq);
    }
}
