package lezhor.htw.zebrakit.analysis;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.movement.nav.Navigator;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A coarse grid of {@link TerritoryUtils#cellPaintValue} scores, smoothed by
 * a convolution kernel so isolated high-value pixels don't dominate target
 * selection. Rebuilt once per tick (shared across all 3 bots of a player)
 * via {@link #update}, then queried per-bot via {@link #bestTargetFor}.
 */
public class InfluenceMap {
    private final int gridSize;
    private final int cellPixels;
    private final int kernelRadiusCells;
    private double[][] values;

    /**
     * @param smoothingRadiusPx how far (in board pixels, not grid cells) a
     *                          high-value cell's influence spreads to its
     *                          neighbors. Expressed in pixels rather than
     *                          cells so the physical smoothing footprint
     *                          stays consistent if gridSize/mapSize changes.
     */
    public InfluenceMap(int gridSize, int mapSize, int smoothingRadiusPx) {
        this.gridSize = gridSize;
        this.cellPixels = mapSize / gridSize;
        this.kernelRadiusCells = Math.max(1, smoothingRadiusPx / cellPixels);
        this.values = new double[gridSize][gridSize];
    }

    public void update(GameState state, int myPlayerNumber) {
        double[][] raw = new double[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                int px = gx * cellPixels + cellPixels / 2;
                int py = gy * cellPixels + cellPixels / 2;
                raw[gx][gy] = TerritoryUtils.cellPaintValue(state, px, py, myPlayerNumber);
            }
        }

        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                double sum = 0;
                for (int dx = -kernelRadiusCells; dx <= kernelRadiusCells; dx++) {
                    for (int dy = -kernelRadiusCells; dy <= kernelRadiusCells; dy++) {
                        int nx = gx + dx;
                        int ny = gy + dy;
                        if (nx < 0 || nx >= gridSize || ny < 0 || ny >= gridSize) continue;
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist <= kernelRadiusCells) {
                            double weight = 1.0 - (dist / (kernelRadiusCells + 1));
                            sum += raw[nx][ny] * weight;
                        }
                    }
                }
                values[gx][gy] = sum;
            }
        }
    }

    public double valueAt(int gx, int gy) {
        return values[gx][gy];
    }

    /**
     * Best target cell (in pixel coordinates) for `self`, favoring high-value
     * nearby cells while dispersing away from where the player's other bots
     * currently are/are headed (so bots spread across the board instead of
     * converging). Falls back to a random walkable point if nothing scores
     * above zero or nothing scoring is reachable.
     */
    public Point bestTargetFor(GameState state, BotContext self, Point[] botPositions, Point[] currentTargets,
                                double alpha, double dispersionSigma, Navigator reachabilityCheck, Random rand) {
        List<CellUtility> candidates = new ArrayList<>();
        Point myPos = botPositions[self.botIndex()];
        double myGx = myPos.x / (double) cellPixels;
        double myGy = myPos.y / (double) cellPixels;

        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                double influence = values[gx][gy];
                if (influence <= 0) continue;

                int px = gx * cellPixels + cellPixels / 2;
                int py = gy * cellPixels + cellPixels / 2;
                if (!state.isWalkable(px, py)) continue;

                double dx = gx - myGx;
                double dy = gy - myGy;
                double distToBot = Math.sqrt(dx * dx + dy * dy);
                double distanceWeight = 1.0 / (1.0 + alpha * distToBot);

                double dispersionWeight = 1.0;
                for (int other = 0; other < botPositions.length; other++) {
                    if (other == self.botIndex()) continue;

                    Point otherPos = botPositions[other];
                    double odx = gx - (otherPos.x / (double) cellPixels);
                    double ody = gy - (otherPos.y / (double) cellPixels);
                    double distToOtherPosSq = odx * odx + ody * ody;
                    dispersionWeight *= (1.0 - Math.exp(-distToOtherPosSq / (2.0 * dispersionSigma * dispersionSigma)));

                    Point otherTarget = currentTargets[other];
                    if (otherTarget != null) {
                        double tdx = gx - (otherTarget.x / (double) cellPixels);
                        double tdy = gy - (otherTarget.y / (double) cellPixels);
                        double distToOtherTargetSq = tdx * tdx + tdy * tdy;
                        dispersionWeight *= (1.0 - Math.exp(-distToOtherTargetSq / (2.0 * dispersionSigma * dispersionSigma)));
                    }
                }

                double utility = influence * distanceWeight * dispersionWeight;
                candidates.add(new CellUtility(new Point(px, py), utility));
            }
        }

        Collections.sort(candidates);

        int limit = Math.min(10, candidates.size());
        for (int i = 0; i < limit; i++) {
            Point candidatePoint = candidates.get(i).point;
            if (reachabilityCheck.isReachable(myPos, candidatePoint)) {
                return candidatePoint;
            }
        }

        return randomWalkableFallback(state, rand);
    }

    private Point randomWalkableFallback(GameState state, Random rand) {
        int mapSize = gridSize * cellPixels;
        while (true) {
            int rx = rand.nextInt(mapSize);
            int ry = rand.nextInt(mapSize);
            if (state.isWalkable(rx, ry)) {
                return new Point(rx, ry);
            }
        }
    }

    private record CellUtility(Point point, double utility) implements Comparable<CellUtility> {
        @Override
        public int compareTo(CellUtility o) {
            return Double.compare(o.utility, this.utility); // descending
        }
    }
}
