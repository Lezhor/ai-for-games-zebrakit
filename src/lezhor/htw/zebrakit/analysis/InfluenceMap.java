package lezhor.htw.zebrakit.analysis;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.movement.nav.Navigator;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A coarse, wall-aware value field over the board, used to pick a general area
 * worth painting. Value is spread by <em>geodesic diffusion</em> — repeated
 * averaging across only walkable-connected neighbours — so a wall blocks
 * propagation entirely (the far side of a wall never bleeds into this side),
 * unlike a plain box/Gaussian blur.
 *
 * The walkable graph is static, so it's precomputed once. The value field is
 * rebuilt <em>from scratch every tick</em> (fresh {@link TerritoryUtils#cellPaintValue}
 * raw values, diffused once) — it never accumulates across ticks, or it would
 * converge to a uniform gray. A second layer diffuses opponent bot positions
 * into a wall-aware "crowding" field, so target selection can avoid contested
 * scrums and favour unattended space.
 */
public class InfluenceMap {
    private final int gridSize;
    private final int cellPixels;
    private final int diffusionIterations;
    private final double diffusionAlpha;

    private boolean[][] walkable;
    private final double[][] value;
    private final double[][] crowd;

    /**
     * @param diffusionIterations how many neighbour-averaging passes (≈ smoothing radius in cells)
     * @param diffusionAlpha      per-pass mixing fraction toward the neighbour mean (0..1)
     */
    public InfluenceMap(int gridSize, int mapSize, int diffusionIterations, double diffusionAlpha) {
        this.gridSize = gridSize;
        this.cellPixels = mapSize / gridSize;
        this.diffusionIterations = diffusionIterations;
        this.diffusionAlpha = diffusionAlpha;
        this.value = new double[gridSize][gridSize];
        this.crowd = new double[gridSize][gridSize];
    }

    /** Precompute the static walkable coarse grid. Idempotent; auto-called by {@link #update}. */
    public void initWalkable(GameState state) {
        if (walkable != null) return;
        walkable = new boolean[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                walkable[gx][gy] = isCellWalkable(state, gx, gy);
            }
        }
    }

    /**
     * A coarse cell is walkable only if its center AND its 4 quadrant-offset
     * points are all walkable — a single center sample can miss a thin wall
     * that doesn't happen to cross it.
     */
    private boolean isCellWalkable(GameState state, int gx, int gy) {
        int cx = cellCenter(gx);
        int cy = cellCenter(gy);
        int offset = cellPixels / 4;
        return state.isWalkable(cx, cy)
                && state.isWalkable(cx - offset, cy - offset)
                && state.isWalkable(cx + offset, cy - offset)
                && state.isWalkable(cx - offset, cy + offset)
                && state.isWalkable(cx + offset, cy + offset);
    }

    /** Convenience overload using the shared default {@link TerritoryUtils#OWN_GAIN_WEIGHT}. */
    public void update(GameState state, int myPlayerNumber, OpponentWeights.Weights weights) {
        update(state, myPlayerNumber, weights, TerritoryUtils.OWN_GAIN_WEIGHT);
    }

    /** Rebuild both layers from the current board. Call once per tick with weights computed once per tick. */
    public void update(GameState state, int myPlayerNumber, OpponentWeights.Weights weights, double ownGainWeight) {
        initWalkable(state);

        double[][] rawValue = new double[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                if (!walkable[gx][gy]) continue;
                rawValue[gx][gy] = TerritoryUtils.cellPaintValue(state, cellCenter(gx), cellCenter(gy), myPlayerNumber, weights, ownGainWeight);
            }
        }
        diffuseInto(rawValue, value);

        double[][] rawCrowd = new double[gridSize][gridSize];
        for (Point opp : state.opponentBotPositions()) {
            int gx = clampGrid(opp.x / cellPixels);
            int gy = clampGrid(opp.y / cellPixels);
            if (walkable[gx][gy]) rawCrowd[gx][gy] += 1.0;
        }
        diffuseInto(rawCrowd, crowd);
    }

    /** K passes of averaging each walkable cell toward the mean of its walkable 4-neighbours. */
    private void diffuseInto(double[][] raw, double[][] out) {
        double[][] a = new double[gridSize][gridSize];
        double[][] b = new double[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            System.arraycopy(raw[gx], 0, a[gx], 0, gridSize);
        }
        for (int it = 0; it < diffusionIterations; it++) {
            for (int gx = 0; gx < gridSize; gx++) {
                for (int gy = 0; gy < gridSize; gy++) {
                    if (!walkable[gx][gy]) {
                        b[gx][gy] = 0.0;
                        continue;
                    }
                    double sum = 0;
                    int n = 0;
                    if (gx > 0 && walkable[gx - 1][gy]) { sum += a[gx - 1][gy]; n++; }
                    if (gx < gridSize - 1 && walkable[gx + 1][gy]) { sum += a[gx + 1][gy]; n++; }
                    if (gy > 0 && walkable[gx][gy - 1]) { sum += a[gx][gy - 1]; n++; }
                    if (gy < gridSize - 1 && walkable[gx][gy + 1]) { sum += a[gx][gy + 1]; n++; }
                    double mean = n > 0 ? sum / n : a[gx][gy];
                    b[gx][gy] = (1.0 - diffusionAlpha) * a[gx][gy] + diffusionAlpha * mean;
                }
            }
            double[][] tmp = a;
            a = b;
            b = tmp;
        }
        for (int gx = 0; gx < gridSize; gx++) {
            System.arraycopy(a[gx], 0, out[gx], 0, gridSize);
        }
    }

    public double valueAt(int gx, int gy) {
        return value[gx][gy];
    }

    /**
     * A general area (pixel point) worth painting for {@code self}: high diffused
     * value, discounted by opponent crowding and by proximity to this player's
     * other bots (dispersion), preferring nearer areas. Randomised among the top
     * reachable candidates so a chaser can't predict/herd us. Falls back to a
     * random walkable point if nothing scores or nothing scoring is reachable.
     */
    public Point bestTargetFor(GameState state, BotContext self, Point[] botPositions, Point[] currentTargets,
                                double distanceAlpha, double dispersionSigma, double crowdStrength,
                                Navigator reachabilityCheck, Random rand) {
        Point myPos = botPositions[self.botIndex()];
        double myGx = myPos.x / (double) cellPixels;
        double myGy = myPos.y / (double) cellPixels;

        List<Candidate> candidates = new ArrayList<>();
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                double influence = value[gx][gy];
                if (influence <= 0 || !walkable[gx][gy]) continue;

                double dx = gx - myGx;
                double dy = gy - myGy;
                double distToBot = Math.sqrt(dx * dx + dy * dy);
                double distanceWeight = 1.0 / (1.0 + distanceAlpha * distToBot);

                double crowdDiscount = 1.0 / (1.0 + crowdStrength * crowd[gx][gy]);

                double dispersionWeight = dispersion(gx, gy, self.botIndex(), botPositions, currentTargets, dispersionSigma);

                double utility = influence * distanceWeight * crowdDiscount * dispersionWeight;
                candidates.add(new Candidate(new Point(cellCenter(gx), cellCenter(gy)), utility));
            }
        }
        if (candidates.isEmpty()) return randomWalkableFallback(state, rand);

        candidates.sort((c1, c2) -> Double.compare(c2.utility, c1.utility));

        // Collect a few reachable top candidates, then pick one weighted by utility.
        List<Candidate> reachable = new ArrayList<>();
        int examined = 0;
        for (Candidate c : candidates) {
            if (examined++ >= 12 || reachable.size() >= 5) break;
            if (reachabilityCheck.isReachable(myPos, c.point)) reachable.add(c);
        }
        if (reachable.isEmpty()) return randomWalkableFallback(state, rand);
        return weightedPick(reachable, rand).point;
    }

    private double dispersion(int gx, int gy, int selfIndex, Point[] botPositions, Point[] currentTargets, double sigma) {
        double weight = 1.0;
        double twoSigmaSq = 2.0 * sigma * sigma;
        for (int other = 0; other < botPositions.length; other++) {
            if (other == selfIndex) continue;
            weight *= repel(gx, gy, botPositions[other], twoSigmaSq);
            if (currentTargets[other] != null) weight *= repel(gx, gy, currentTargets[other], twoSigmaSq);
        }
        return weight;
    }

    private double repel(int gx, int gy, Point other, double twoSigmaSq) {
        double dx = gx - (other.x / (double) cellPixels);
        double dy = gy - (other.y / (double) cellPixels);
        return 1.0 - Math.exp(-(dx * dx + dy * dy) / twoSigmaSq);
    }

    private Candidate weightedPick(List<Candidate> items, Random rand) {
        double total = 0;
        for (Candidate c : items) total += c.utility;
        if (total <= 0) return items.get(0);
        double r = rand.nextDouble() * total;
        for (Candidate c : items) {
            r -= c.utility;
            if (r <= 0) return c;
        }
        return items.get(items.size() - 1);
    }

    private Point randomWalkableFallback(GameState state, Random rand) {
        int mapSize = gridSize * cellPixels;
        for (int tries = 0; tries < 1000; tries++) {
            int rx = rand.nextInt(mapSize);
            int ry = rand.nextInt(mapSize);
            if (state.isWalkable(rx, ry)) return new Point(rx, ry);
        }
        return new Point(mapSize / 2, mapSize / 2);
    }

    private int cellCenter(int gridIndex) {
        return gridIndex * cellPixels + cellPixels / 2;
    }

    private int clampGrid(int i) {
        if (i < 0) return 0;
        if (i >= gridSize) return gridSize - 1;
        return i;
    }

    private record Candidate(Point point, double utility) {
    }
}
