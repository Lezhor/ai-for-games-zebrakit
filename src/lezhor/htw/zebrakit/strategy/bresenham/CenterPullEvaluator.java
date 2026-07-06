package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.GameState;

/**
 * A static, circular field centred on the board that gently pulls bots inward,
 * countering their tendency to drift along the edges. Purely geometric (no board
 * state), so it needs no per-tick update:
 * <ul>
 *   <li>within {@code INNER_RADIUS_PX} of centre → constant {@code INNER_REWARD};</li>
 *   <li>beyond {@code OUTER_RADIUS_PX} → constant {@code OUTER_REWARD};</li>
 *   <li>between the two radii → linearly interpolated between the two rewards.</li>
 * </ul>
 * Keep both rewards {@code >= 0} (e.g. inner high, outer 0) to stay non-negative like
 * the other evaluators. To make the pull stronger set {@code INNER_REWARD} well above
 * {@code OUTER_REWARD} and/or raise the outer {@code WEIGHT}.
 */
public final class CenterPullEvaluator implements PixelEvaluator {
    private static final double CENTER = BoardConstants.MAP_SIZE / 2.0;

    private double outerWeight;
    private double innerRadius;
    private double outerRadius;
    private double innerReward;
    private double outerReward;

    @Override
    public void init(GameState state, BresenhamConfig config) {
        this.outerWeight = config.centerPullWeight();
        this.innerRadius = config.centerInnerRadiusPx();
        this.outerRadius = config.centerOuterRadiusPx();
        this.innerReward = config.centerInnerReward();
        this.outerReward = config.centerOuterReward();
    }

    @Override
    public double valueAt(int x, int y, GameState state, BotContext bot) {
        double dx = x - CENTER;
        double dy = y - CENTER;
        double r = Math.sqrt(dx * dx + dy * dy);
        if (r <= innerRadius) return innerReward;
        if (r >= outerRadius) return outerReward;
        // Linear interpolation from innerReward (at innerRadius) to outerReward (at outerRadius).
        // outerRadius > innerRadius here, so the denominator is safe.
        double t = (r - innerRadius) / (outerRadius - innerRadius);
        return innerReward + t * (outerReward - innerReward);
    }

    @Override
    public double weight() {
        return outerWeight;
    }
}
