package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.analysis.LocalPaintField;
import lezhor.htw.zebrakit.analysis.OpponentWeights;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * Steers toward the most valuable nearby space using a wall-aware
 * {@link LocalPaintField} (diffused {@link TerritoryUtils#cellPaintValue} in a
 * window around the bot) — our own gain plus stripped opponent value, weighted
 * toward whoever is leading. This is the dominant local driver: a bot drifts
 * through the best paint value around it (including white cells, which strip
 * both opponents) rather than beelining to a target pixel. Unlike raw ring
 * sampling, the diffusion won't reach value across a wall.
 */
public class SeekPaintValueBehavior implements SteeringBehavior {
    private final LocalPaintField field;

    /**
     * @param windowRadiusCells fine cells sampled in each direction around the bot
     * @param fineCellPixels    size of each fine cell in board pixels
     * @param diffusionIterations / @param diffusionAlpha  local diffusion smoothing
     */
    public SeekPaintValueBehavior(int windowRadiusCells, int fineCellPixels, int diffusionIterations, double diffusionAlpha) {
        this.field = new LocalPaintField(windowRadiusCells, fineCellPixels, diffusionIterations, diffusionAlpha);
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        int myPlayerNumber = state.myPlayerNumber();
        OpponentWeights.Weights weights = TerritoryUtils.computeWeights(state, myPlayerNumber, OpponentWeights.DEFAULT_SIGMA);
        return field.bestDirection(state, currentPos, myPlayerNumber, weights);
    }
}
