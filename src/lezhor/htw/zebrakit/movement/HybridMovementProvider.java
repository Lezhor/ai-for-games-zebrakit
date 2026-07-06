package lezhor.htw.zebrakit.movement;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.movement.steering.SteeringMovementProvider;

import java.awt.Point;

/**
 * Blends a macro {@link Navigator} (coarse pathfinding toward a target area)
 * with a micro {@link SteeringMovementProvider} (local behaviours: value-seeking,
 * own-bot separation). The target is a <em>general area to paint in</em>, not a
 * precise pixel, so the blend is <b>micro-dominant</b>: local color-seeking
 * drives movement, and the macro path only contributes a gentle <b>restoring
 * leash</b> that grows with distance once the bot strays past {@code areaRadiusPx}
 * of its area — near the area it's ~0 (wander freely for color), far outside it
 * grows to {@code macroGain} to pull the bot back. Own territory is never an
 * obstacle; only real walls block the macro path.
 */
public class HybridMovementProvider implements MovementProvider {
    private final Navigator macro;
    private final SteeringMovementProvider micro;
    private final double microWeight;
    private final double areaRadiusPx;
    private final double leashRangePx;
    private final double macroGain;

    public HybridMovementProvider(Navigator macro, SteeringMovementProvider micro,
                                  double microWeight, double areaRadiusPx, double leashRangePx, double macroGain) {
        this.macro = macro;
        this.micro = micro;
        this.microWeight = microWeight;
        this.areaRadiusPx = areaRadiusPx;
        this.leashRangePx = leashRangePx;
        this.macroGain = macroGain;
    }

    @Override
    public void initialize(long seed) {
        macro.initialize(seed);
        micro.initialize(seed);
    }

    @Override
    public void initialize(lenz.htw.zebrakit.net.NetworkClient client) {
        macro.initialize(client);
        micro.initialize(client);
    }

    @Override
    public Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos) {
        Vector2 microDir = micro.getNextMoveDirection(state, self, currentPos, targetPos);

        double distToTarget = currentPos.distance(targetPos);
        double leash = macroGain * clamp01((distToTarget - areaRadiusPx) / Math.max(1.0, leashRangePx));

        Vector2 combined = microDir.scaled(microWeight);
        if (leash > 0) {
            Vector2 macroDir = macro.getNextMoveDirection(state, self, currentPos, targetPos);
            combined = combined.plus(macroDir.scaled(leash));
        }
        return combined.normalized();
    }

    @Override
    public boolean isReachable(Point currentPos, Point targetPos) {
        return macro.isReachable(currentPos, targetPos);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
