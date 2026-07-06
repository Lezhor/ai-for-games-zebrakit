package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.GameState;

/**
 * Builds {@link PlayerWeights} from the players' live scores, leader-scaled: the
 * higher an opponent's score, the more we weight stripping value from it, so we
 * prioritise taking points off whoever is winning. Per opponent {@code o}:
 * <pre>weight_o = ENEMY_BASE + LEADER_SCALE * (score_o / maxOpponentScore)</pre>
 * (the top opponent contributes the full {@code LEADER_SCALE} bump). Our own
 * channel gets weight 0 — we never strip from ourselves. Recompute once per tick.
 */
public final class LeaderWeighting {
    private LeaderWeighting() {
    }

    public static PlayerWeights compute(GameState state, int myPlayerNumber, BresenhamConfig config) {
        long maxOpponentScore = 0;
        for (int p = 0; p < GameState.PLAYER_COUNT; p++) {
            if (p == myPlayerNumber) continue;
            maxOpponentScore = Math.max(maxOpponentScore, state.getScore(p));
        }
        double denom = Math.max(1.0, (double) maxOpponentScore);

        double[] byPlayer = new double[GameState.PLAYER_COUNT];
        for (int p = 0; p < GameState.PLAYER_COUNT; p++) {
            if (p == myPlayerNumber) continue;
            byPlayer[p] = config.enemyBase() + config.leaderScale() * (state.getScore(p) / denom);
        }
        return new PlayerWeights(byPlayer);
    }
}
