package lezhor.htw.zebrakit.strategy.bresenham;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An on-demand ("lambda") evaluator that steers bots clear of SLOW powerups (which
 * only hurt whoever grabs them). Each pixel scores 1 when it is clear of every active
 * SLOW pickup and 0 when it falls within a SLOW's pickup radius. Given an extremely
 * high {@code WEIGHT}, a ray passing near a SLOW loses those pixels' reward and is
 * therefore rejected in favour of a clear ray — so all SLOW pickups are avoided.
 *
 * <p>Non-negative like the other evaluators (clear = 1, near a SLOW = 0 — never a
 * penalty). When no SLOW powerups are active it returns 1 everywhere, a constant that
 * cancels out across directions and so has no effect. SLOW positions are snapshotted
 * once per tick in {@link #update} so the per-pixel {@link #valueAt} allocates nothing.
 */
public final class SlowAvoidEvaluator implements PixelEvaluator {
    private double outerWeight;
    private double radiusSq;

    private Point[] slowPowerups = new Point[0];

    @Override
    public void init(GameState state, BresenhamConfig config) {
        this.outerWeight = config.slowAvoidWeight();
        double radius = config.slowAvoidRadiusPx();
        this.radiusSq = radius * radius;
    }

    @Override
    public void update(GameState state, int myPlayerNumber, PlayerWeights weights) {
        List<Point> slow = new ArrayList<>();
        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            if (entry.getValue() == PowerupType.SLOW) slow.add(entry.getKey());
        }
        slowPowerups = slow.toArray(new Point[0]);
    }

    @Override
    public double valueAt(int x, int y, GameState state, BotContext bot) {
        for (Point p : slowPowerups) {
            double dx = x - p.x;
            double dy = y - p.y;
            if (dx * dx + dy * dy <= radiusSq) return 0.0; // inside a SLOW pickup radius — no reward
        }
        return 1.0; // clear of every SLOW pickup
    }

    @Override
    public double weight() {
        return outerWeight;
    }
}
