package lezhor.htw.zebrakit.analysis;

import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * A small, high-resolution, wall-aware value field computed in a window around
 * a single bot each tick — the fine counterpart to {@link InfluenceMap}'s coarse
 * global field. Where the coarse field picks a general area, this decides which
 * exact nearby direction the bot should drift to grab the most paint value,
 * diffusing {@link TerritoryUtils#cellPaintValue} across only walkable-connected
 * fine cells so it steers toward value without leaking across walls.
 *
 * Stateless per call (windowed → cheap); holds only its resolution parameters.
 */
public class LocalPaintField {
    private final int windowRadiusCells;
    private final int cellPixels;
    private final int diffusionIterations;
    private final double diffusionAlpha;

    public LocalPaintField(int windowRadiusCells, int cellPixels, int diffusionIterations, double diffusionAlpha) {
        this.windowRadiusCells = windowRadiusCells;
        this.cellPixels = cellPixels;
        this.diffusionIterations = diffusionIterations;
        this.diffusionAlpha = diffusionAlpha;
    }

    /**
     * Unit direction (or {@link Vector2#ZERO}) toward the highest-value nearby
     * walkable space — the diffused-value centroid of the window relative to
     * {@code center}. Uniform surroundings → ~ZERO (no local preference).
     *
     * @param minRadiusPx cells closer than this to {@code center} are ramped down toward zero
     *                     weight (a cell nearly under the bot barely offsets the centroid, so
     *                     its value noise can flip the resulting direction's sign tick-to-tick) —
     *                     linearly from 0 at the center to full weight at {@code minRadiusPx}, so
     *                     there's no hard-edged ring that the bot can end up orbiting.
     *                     {@code <= 0} disables the ramp (all cells full weight).
     * @param ownGainWeight how much our own channel's headroom is weighted vs. opponent strip —
     *                      see {@link TerritoryUtils#cellPaintValue}; higher biases hard toward
     *                      enemy-owned cells over neutral white ones.
     * @param previousDirection the direction chosen last tick (or {@link Vector2#ZERO} if none yet).
     *                          Cells roughly in this same direction from the bot get a weight bonus,
     *                          so a temporarily lower-value-but-consistent direction can beat a
     *                          marginally higher-value one on the opposite side — without this, the
     *                          centroid can keep re-picking whichever side of an already-painted
     *                          patch still has the most remaining value, tracing its rim in a circle.
     * @param persistenceStrength how strong that directional-consistency bonus is (0 disables it);
     *                            a cell exactly in {@code previousDirection} gets {@code (1 + persistenceStrength)}×
     *                            weight, tapering to 1× (no bonus) for a cell perpendicular or behind.
     */
    public Vector2 bestDirection(GameState state, Point center, int myPlayerNumber, OpponentWeights.Weights weights,
                                  double minRadiusPx, double ownGainWeight, Vector2 previousDirection, double persistenceStrength) {
        int size = 2 * windowRadiusCells + 1;
        double[][] a = new double[size][size];
        boolean[][] walkable = new boolean[size][size];

        for (int ix = 0; ix < size; ix++) {
            for (int iy = 0; iy < size; iy++) {
                int wx = center.x + (ix - windowRadiusCells) * cellPixels;
                int wy = center.y + (iy - windowRadiusCells) * cellPixels;
                boolean ok = state.isWalkable(wx, wy);
                walkable[ix][iy] = ok;
                a[ix][iy] = ok ? TerritoryUtils.cellPaintValue(state, wx, wy, myPlayerNumber, weights, ownGainWeight) : 0.0;
            }
        }

        double[][] b = new double[size][size];
        for (int it = 0; it < diffusionIterations; it++) {
            for (int ix = 0; ix < size; ix++) {
                for (int iy = 0; iy < size; iy++) {
                    if (!walkable[ix][iy]) { b[ix][iy] = 0.0; continue; }
                    double sum = 0;
                    int n = 0;
                    if (ix > 0 && walkable[ix - 1][iy]) { sum += a[ix - 1][iy]; n++; }
                    if (ix < size - 1 && walkable[ix + 1][iy]) { sum += a[ix + 1][iy]; n++; }
                    if (iy > 0 && walkable[ix][iy - 1]) { sum += a[ix][iy - 1]; n++; }
                    if (iy < size - 1 && walkable[ix][iy + 1]) { sum += a[ix][iy + 1]; n++; }
                    double mean = n > 0 ? sum / n : a[ix][iy];
                    b[ix][iy] = (1.0 - diffusionAlpha) * a[ix][iy] + diffusionAlpha * mean;
                }
            }
            double[][] tmp = a; a = b; b = tmp;
        }

        boolean hasPersistence = persistenceStrength > 0 && !previousDirection.isZero();
        Vector2 prevUnit = hasPersistence ? previousDirection.normalized() : Vector2.ZERO;

        // Value-weighted centroid direction relative to the center cell.
        double sumX = 0, sumY = 0, sumW = 0;
        for (int ix = 0; ix < size; ix++) {
            for (int iy = 0; iy < size; iy++) {
                if (!walkable[ix][iy]) continue;
                double dx = ix - windowRadiusCells;
                double dy = iy - windowRadiusCells;
                double dist = Math.hypot(dx, dy);
                double radialWeight = minRadiusPx > 0
                        ? Math.min(1.0, (dist * cellPixels) / minRadiusPx)
                        : 1.0;
                double persistenceBonus = 1.0;
                if (hasPersistence && dist > 0) {
                    double cosSim = (dx * prevUnit.x() + dy * prevUnit.y()) / dist;
                    persistenceBonus = 1.0 + persistenceStrength * Math.max(0, cosSim);
                }
                double w = a[ix][iy] * radialWeight * persistenceBonus;
                if (w <= 0) continue;
                sumX += w * dx;
                sumY += w * dy;
                sumW += w;
            }
        }
        if (sumW <= 0) return Vector2.ZERO;
        return new Vector2(sumX / sumW, sumY / sumW).normalized();
    }
}
