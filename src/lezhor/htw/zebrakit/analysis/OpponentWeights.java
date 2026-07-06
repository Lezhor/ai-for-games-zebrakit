package lezhor.htw.zebrakit.analysis;

/**
 * How much to weight "stripping value from" each of the two opponents, as a
 * bump over the <em>normalized score margin</em> between them and us:
 * <pre>
 *   mₒ = (scoreₒ − myScore) / totalScore        // ~[-1, 1]
 *   wₒ = FLOOR + PEAK · exp( −(mₒ − MU)² / 2σ² )
 * </pre>
 * The bump peaks where an opponent's score is near ours — the pivotal race —
 * so we hammer whoever we're actually contesting for a placement, chase a
 * catchable leader, defend a close pursuer, and give up on an uncatchable
 * runaway (secure our place) — all continuously, no thresholds. σ is the
 * spread: wide early (everyone catchable) → narrow late (lock onto our
 * standings-neighbour), driven by match progress at the call site.
 *
 * The MU/FLOOR/PEAK shape is intentionally shared here rather than per-strategy
 * because the influence map, the pathfinding color-cost, and the steering all
 * consume it and must agree.
 */
public final class OpponentWeights {
    private OpponentWeights() {
    }

    /** Slight bias toward opponents just ahead of us (climbing a place beats defending one). */
    private static final double MU = 0.05;
    /** Far-off opponents stay slightly worth stripping, but never a priority. */
    private static final double FLOOR = 0.5;
    /** Extra weight on the pivotal (near-tied) opponent at the peak of the bump. */
    private static final double PEAK = 2.5;

    /** Wide default spread for standalone use (e.g. the navigator with no live match progress). */
    public static final double DEFAULT_SIGMA = 0.5;

    public record Weights(double weightA, double weightB) {
    }

    /**
     * @param sigma bump spread in normalized-margin units (fraction of total score in play).
     */
    public static Weights compute(long myScore, long scoreA, long scoreB, double sigma) {
        double total = Math.max(1.0, (double) myScore + scoreA + scoreB);
        double s = Math.max(1e-6, sigma);
        return new Weights(
                weight((scoreA - myScore) / total, s),
                weight((scoreB - myScore) / total, s));
    }

    private static double weight(double margin, double sigma) {
        double d = margin - MU;
        return FLOOR + PEAK * Math.exp(-(d * d) / (2.0 * sigma * sigma));
    }
}
