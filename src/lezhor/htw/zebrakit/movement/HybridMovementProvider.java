package lezhor.htw.zebrakit.movement;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.movement.steering.SteeringMovementProvider;

import java.awt.Point;

/**
 * Blends a macro {@link Navigator} (coarse pathfinding toward a target) with
 * a micro {@link SteeringMovementProvider} (local behaviors: separation,
 * hazard-avoidance, value-seeking). Far from the target, movement is mostly
 * the macro path; within `blendRadiusPx` of the target, the micro behaviors
 * increasingly take over — so bots spread out / wander locally near a shared
 * destination instead of converging on one exact point, and can continuously
 * bias away from hazards or toward valuable cells along the way.
 */
public class HybridMovementProvider implements MovementProvider {
    private final Navigator macro;
    private final SteeringMovementProvider micro;
    private final double blendRadiusPx;

    public HybridMovementProvider(Navigator macro, SteeringMovementProvider micro, double blendRadiusPx) {
        this.macro = macro;
        this.micro = micro;
        this.blendRadiusPx = blendRadiusPx;
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
        Vector2 macroDir = macro.getNextMoveDirection(state, self, currentPos, targetPos);
        Vector2 microDir = micro.getNextMoveDirection(state, self, currentPos, targetPos);

        double distToTarget = currentPos.distance(targetPos);
        double microWeight = Math.max(0.0, Math.min(1.0, 1.0 - distToTarget / blendRadiusPx));

        return macroDir.scaled(1.0 - microWeight).plus(microDir.scaled(microWeight)).normalized();
    }

    @Override
    public boolean isReachable(Point currentPos, Point targetPos) {
        return macro.isReachable(currentPos, targetPos);
    }
}
