package lezhor.htw.zebrakit.strategy.support;

import java.awt.Point;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Globally greedy nearest-first assignment of bots to candidate points,
 * lowest-cost pair first. The cost function is caller-supplied (e.g. path
 * distance, or distance/speed for a "time to reach" estimate that accounts
 * for each bot's own speed) rather than fixed to plain distance, so the same
 * assignment logic works for any bot-to-target matching problem.
 */
public final class GreedyAssignment {
    private GreedyAssignment() {
    }

    private record Pair(int botIndex, Point candidate, double cost) implements Comparable<Pair> {
        @Override
        public int compareTo(Pair o) {
            return Double.compare(cost, o.cost);
        }
    }

    /**
     * @param botCount   number of bots to assign (indices 0..botCount-1)
     * @param candidates candidate target points
     * @param cost       (botIndex, candidate) -> cost; return Double.MAX_VALUE for an unreachable/invalid pair
     * @return bot index -> assigned candidate, for however many pairs could be greedily matched
     */
    public static Map<Integer, Point> assignLowestCostFirst(int botCount, Collection<Point> candidates,
                                                             BiFunction<Integer, Point, Double> cost) {
        List<Pair> pairs = new ArrayList<>();
        for (int bot = 0; bot < botCount; bot++) {
            for (Point candidate : candidates) {
                double c = cost.apply(bot, candidate);
                if (c < Double.MAX_VALUE) {
                    pairs.add(new Pair(bot, candidate, c));
                }
            }
        }
        Collections.sort(pairs);

        Map<Integer, Point> assignment = new HashMap<>();
        Set<Point> takenCandidates = new HashSet<>();
        for (Pair pair : pairs) {
            if (assignment.containsKey(pair.botIndex()) || takenCandidates.contains(pair.candidate())) continue;
            assignment.put(pair.botIndex(), pair.candidate());
            takenCandidates.add(pair.candidate());
        }
        return assignment;
    }
}
