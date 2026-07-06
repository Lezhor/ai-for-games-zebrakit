package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;

/**
 * The one real evaluator: prefers pixels where <em>our</em> colour is missing
 * (headroom to gain) and <em>enemy</em> colour is present (points to strip):
 * <pre>value = OWN_WEIGHT*(255 - ourChannel) + Σ_opponent weight_o * theirChannel</pre>
 * Always non-negative (our own colour yields 0, never a penalty), so ray directions
 * keep a usable ordering even when a bot is surrounded by its own colour.
 * The per-opponent weights are injected each tick ({@link PlayerWeights}, leader-scaled),
 * not computed here. The field is kept on a downscaled grid so the per-tick blur is
 * cheap; the blur is a plain box average that "thickens" a thin ray, so a 1px-wide ray
 * still picks up nearby colour mass. Walls (board value 0) score 0.
 */
public final class PaintValueEvaluator implements PixelEvaluator {
    private double outerWeight;
    private double ownWeight;
    private int cellPixels;
    private int gridSize;
    private int blurIterations;

    private double[][] value;
    private double[][] scratch;

    @Override
    public void init(GameState state, BresenhamConfig config) {
        this.outerWeight = config.paintValueWeight();
        this.ownWeight = config.ownWeight();
        this.cellPixels = config.paintValueGridScale();
        this.gridSize = BoardConstants.MAP_SIZE / config.paintValueGridScale();
        this.blurIterations = config.paintValueBlurIterations();
        this.value = new double[gridSize][gridSize];
        this.scratch = new double[gridSize][gridSize];
    }

    @Override
    public void update(GameState state, int myPlayerNumber, PlayerWeights weights) {
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                value[gx][gy] = rawValueAt(state, cellCenter(gx), cellCenter(gy), myPlayerNumber, weights);
            }
        }
        blur();
    }

    private double rawValueAt(GameState state, int x, int y, int myPlayerNumber, PlayerWeights weights) {
        int packed = state.getBoard(x, y);
        if (packed == 0) return 0; // wall
        // Non-negative by design: our own colour just yields no reward (headroom 255-ours is 0 when
        // we're maxed there), it is never a penalty. Kept non-negative so directions always have a
        // usable ordering — a bot ringed by its own colour still gets a gradient toward the least-bad
        // way out instead of a flat field of equal negatives. To favour foreign colour, raise the
        // enemy weights (ENEMY_BASE / LEADER_SCALE), not lower our own below zero.
        double total = ownWeight * (255 - TerritoryUtils.myChannelValue(packed, myPlayerNumber));
        for (int p = 0; p < GameState.PLAYER_COUNT; p++) {
            if (p == myPlayerNumber) continue;
            total += weights.weightForPlayer(p) * TerritoryUtils.myChannelValue(packed, p);
        }
        return total;
    }

    /** K passes of averaging each cell with its in-bounds 4-neighbours (plain, wall-agnostic box blur). */
    private void blur() {
        for (int it = 0; it < blurIterations; it++) {
            for (int gx = 0; gx < gridSize; gx++) {
                for (int gy = 0; gy < gridSize; gy++) {
                    double sum = value[gx][gy];
                    int n = 1;
                    if (gx > 0) { sum += value[gx - 1][gy]; n++; }
                    if (gx < gridSize - 1) { sum += value[gx + 1][gy]; n++; }
                    if (gy > 0) { sum += value[gx][gy - 1]; n++; }
                    if (gy < gridSize - 1) { sum += value[gx][gy + 1]; n++; }
                    scratch[gx][gy] = sum / n;
                }
            }
            double[][] tmp = value;
            value = scratch;
            scratch = tmp;
        }
    }

    @Override
    public double valueAt(int x, int y, GameState state, BotContext bot) {
        return value[clamp(x / cellPixels)][clamp(y / cellPixels)];
    }

    @Override
    public double weight() {
        return outerWeight;
    }

    private int cellCenter(int gridIndex) {
        return gridIndex * cellPixels + cellPixels / 2;
    }

    private int clamp(int i) {
        if (i < 0) return 0;
        if (i >= gridSize) return gridSize - 1;
        return i;
    }
}
