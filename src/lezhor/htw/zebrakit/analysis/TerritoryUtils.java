package lezhor.htw.zebrakit.analysis;

import lezhor.htw.zebrakit.core.GameState;

import java.awt.Point;

/** Pure helpers for reasoning about painted board cells (packed RGB channel values). */
public final class TerritoryUtils {
    private TerritoryUtils() {
    }

    public static boolean isNear(Point a, Point b, double radiusPx) {
        if (a == null || b == null) return false;
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return (dx * dx + dy * dy) < radiusPx * radiusPx;
    }

    /** Extracts the given player's channel value (0-255) from a packed RGB board cell. */
    public static int myChannelValue(int packedColor, int myPlayerNumber) {
        int r = (packedColor >> 16) & 0xFF;
        int g = (packedColor >> 8) & 0xFF;
        int b = packedColor & 0xFF;
        return switch (myPlayerNumber) {
            case 0 -> r;
            case 1 -> g;
            default -> b;
        };
    }

    /** True if `target` is a wall, or already painted above `threshold` in our own channel. */
    public static boolean isPainted(GameState state, Point target, int myPlayerNumber, int threshold) {
        int val = state.getBoard(target.x, target.y);
        if (val == 0) return true; // wall is considered painted/useless
        return myChannelValue(val, myPlayerNumber) > threshold;
    }

    /** How much our own channel gain is worth relative to a point of opponent-channel strip (see {@link #cellPaintValue}). */
    public static final double OWN_GAIN_WEIGHT = 2.0;

    /** The other player index in ascending order excluding myPlayerNumber (the lower of the two). */
    public static int otherPlayerA(int myPlayerNumber) {
        return myPlayerNumber == 0 ? 1 : 0;
    }

    /** The other player index in ascending order excluding myPlayerNumber (the higher of the two). */
    public static int otherPlayerB(int myPlayerNumber) {
        return myPlayerNumber == 2 ? 1 : 2;
    }

    /**
     * The scoring value of painting one cell our color: our own channel's
     * headroom (0-255, weighted by {@link #OWN_GAIN_WEIGHT} to prioritize it)
     * PLUS how much we'd strip from both opponents' channels, per
     * {@link OpponentWeights} (so a clearly-leading opponent is targeted
     * harder). This is why fully white/unclaimed cells (255 in every
     * channel) are valuable even though they give no direct gain to us —
     * painting them still strips full value from both opponents.
     */
    public static double cellPaintValue(int myVal, int otherAVal, int otherBVal, OpponentWeights.Weights weights) {
        return OWN_GAIN_WEIGHT * (255 - myVal) + weights.weightA() * otherAVal + weights.weightB() * otherBVal;
    }

    /** Convenience overload: reads the board cell and both opponents' live scores from `state`. Returns 0 for walls. */
    public static double cellPaintValue(GameState state, int x, int y, int myPlayerNumber) {
        int val = state.getBoard(x, y);
        if (val == 0) return 0;

        int otherA = otherPlayerA(myPlayerNumber);
        int otherB = otherPlayerB(myPlayerNumber);
        OpponentWeights.Weights weights = OpponentWeights.compute(state.getScore(otherA), state.getScore(otherB));

        return cellPaintValue(myChannelValue(val, myPlayerNumber), myChannelValue(val, otherA), myChannelValue(val, otherB), weights);
    }
}
