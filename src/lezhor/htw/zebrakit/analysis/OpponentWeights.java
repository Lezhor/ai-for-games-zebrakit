package lezhor.htw.zebrakit.analysis;

/**
 * How much to weight "stripping value from" each of the two opponents,
 * proportional to their current score — so an opponent who is clearly
 * leading gets targeted harder, while near-equal opponents are weighted
 * about equally. Shared by pathfinding color-cost, the influence map, and
 * value-seeking steering so leader-targeting behaves consistently
 * everywhere instead of three separate ad-hoc formulas.
 */
public final class OpponentWeights {
    private OpponentWeights() {
    }

    public record Weights(double weightA, double weightB) {
    }

    public static Weights compute(long scoreA, long scoreB) {
        double total = Math.max(1, scoreA + scoreB);
        return new Weights(1.0 + 2.0 * (scoreA / total), 1.0 + 2.0 * (scoreB / total));
    }
}
