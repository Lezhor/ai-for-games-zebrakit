package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.GameState;

/**
 * Per-opponent "strip weight" for one tick: how much stripping a point from each
 * player's colour channel is worth to us. Computed once per tick by
 * {@link LeaderWeighting} from live scores and injected into evaluators, so the
 * leader-targeting logic lives in one tweakable place shared by every evaluator.
 * Our own channel is never a strip target, so its entry is 0.
 */
public final class PlayerWeights {
    private final double[] byPlayer;

    PlayerWeights(double[] byPlayer) {
        this.byPlayer = byPlayer;
    }

    /** Strip weight for the given player's colour channel (0 for our own player). */
    public double weightForPlayer(int player) {
        return byPlayer[player];
    }
}
