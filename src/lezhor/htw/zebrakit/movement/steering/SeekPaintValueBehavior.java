package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.analysis.LocalPaintField;
import lezhor.htw.zebrakit.analysis.OpponentWeights;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
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
    private final double minRadiusMultiplier;
    private final double ownGainWeight;
    private final double persistenceStrength;
    // Own state (not the strategy's post-hoc turn-rate lastDirection): the direction this
    // behavior itself picked last tick per bot, fed back in as the persistence bonus anchor.
    private final Vector2[] lastLocalDirection = new Vector2[BotRoles.BOT_COUNT];

    /**
     * @param windowRadiusCells fine cells sampled in each direction around the bot
     * @param fineCellPixels    size of each fine cell in board pixels
     * @param diffusionIterations / @param diffusionAlpha  local diffusion smoothing
     * @param minRadiusMultiplier cells within {@code minRadiusMultiplier * self.paintRadius()} of
     *                            the bot are ramped down toward zero weight (see
     *                            {@link LocalPaintField#bestDirection}); {@code <= 0} disables the ramp
     * @param ownGainWeight     see {@link TerritoryUtils#cellPaintValue} — enemy-vs-white bias
     * @param persistenceStrength directional-consistency bonus toward last tick's chosen direction
     *                            (see {@link LocalPaintField#bestDirection}); 0 disables it
     */
    public SeekPaintValueBehavior(int windowRadiusCells, int fineCellPixels, int diffusionIterations, double diffusionAlpha,
                                   double minRadiusMultiplier, double ownGainWeight, double persistenceStrength) {
        this.field = new LocalPaintField(windowRadiusCells, fineCellPixels, diffusionIterations, diffusionAlpha);
        this.minRadiusMultiplier = minRadiusMultiplier;
        this.ownGainWeight = ownGainWeight;
        this.persistenceStrength = persistenceStrength;
        for (int i = 0; i < lastLocalDirection.length; i++) lastLocalDirection[i] = Vector2.ZERO;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        int myPlayerNumber = state.myPlayerNumber();
        OpponentWeights.Weights weights = TerritoryUtils.computeWeights(state, myPlayerNumber, OpponentWeights.DEFAULT_SIGMA);
        double minRadiusPx = minRadiusMultiplier * self.paintRadius();
        Vector2 previous = lastLocalDirection[self.botIndex()];
        Vector2 chosen = field.bestDirection(state, currentPos, myPlayerNumber, weights, minRadiusPx, ownGainWeight,
                previous, persistenceStrength);
        if (!chosen.isZero()) lastLocalDirection[self.botIndex()] = chosen;
        return chosen;
    }
}
