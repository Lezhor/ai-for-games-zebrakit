package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;

/**
 * Scores a single board pixel for the ray scan. {@link RayScanner} walks each
 * Bresenham ray and, for every pixel, sums {@code weight() * valueAt(...)} over
 * all evaluators (then applies the distance falloff). Higher = more worth moving
 * toward.
 *
 * <p>Two flavours share this interface: <em>grid-lookup</em> evaluators precompute
 * a (usually downscaled) 2D field in {@link #update} and just index it in
 * {@link #valueAt} (e.g. {@link PaintValueEvaluator}); <em>on-demand</em>
 * evaluators leave {@code update} empty and compute in {@code valueAt} (e.g. a
 * future bot-separation evaluator). Parameters are read once in {@link #init} so
 * the per-pixel {@code valueAt} hot path stays lean.
 */
public interface PixelEvaluator {

    /** One-time setup: read config, size any grids. Called once before the first tick. */
    void init(GameState state, BresenhamConfig config);

    /**
     * Per-tick refresh, called before any ray is cast. Grid-lookup evaluators rebuild
     * their field here from the current board and the injected {@link PlayerWeights}.
     */
    default void update(GameState state, int myPlayerNumber, PlayerWeights weights) {
    }

    /** Value of one board pixel for {@code bot}. Hot path — keep it cheap. */
    double valueAt(int x, int y, GameState state, BotContext bot);

    /** This evaluator's outer weight, relative to the others in the list. */
    double weight();
}
