package lezhor.htw.zebrakit.strategy.bresenham;

import lenz.htw.zebrakit.PowerupType;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.movement.nav.Navigator;
import lezhor.htw.zebrakit.strategy.support.GreedyAssignment;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The clean powerup override on top of the raycasting: decides which bots should
 * peel off to grab a bomb/rain, using the same race-aware greedy assignment as the
 * other strategies. A powerup is a candidate only if it is within reach, worth more
 * than the ground we'd give up, and we can beat the opponents to it (our path
 * distance vs their straight-line distance × race margin). SLOW is never chased.
 * Bots assigned here route to the powerup with the plain navigator — the deliberate
 * "no pathfinding <em>for the most part</em>" exception.
 */
public final class PowerupOverride {
    private final Navigator powerupNav;
    private final BresenhamConfig config;

    public PowerupOverride(Navigator powerupNav, BresenhamConfig config) {
        this.powerupNav = powerupNav;
        this.config = config;
    }

    /** bot index -> powerup point, for the bots (if any) that should chase one this tick. */
    public Map<Integer, Point> assign(GameState state, BotContext[] bots, Point[] positions) {
        if (!config.powerupsEnabled()) return Map.of();

        List<Point> winnable = new ArrayList<>();
        List<Point> opponents = state.opponentBotPositions();
        for (Map.Entry<Point, PowerupType> entry : state.activePowerups().entrySet()) {
            if (entry.getValue() == PowerupType.SLOW) continue;
            Point p = entry.getKey();
            if (withinReach(positions, p) && weCanWin(p, positions, opponents)) winnable.add(p);
        }
        if (winnable.isEmpty()) return Map.of();

        int myPlayerNumber = state.myPlayerNumber();
        return GreedyAssignment.assignLowestCostFirst(BotRoles.BOT_COUNT, winnable, (bot, p) -> {
            double dist = powerupNav.getPathDistance(positions[bot], p);
            if (dist >= Double.MAX_VALUE || dist > config.powerupMaxPathDist()) return Double.MAX_VALUE;
            double value = powerupValue(state, p, myPlayerNumber);
            double timeToReach = dist / bots[bot].speed();
            return timeToReach / Math.max(1.0, value); // lower = better: fast to reach and/or high value
        });
    }

    private boolean withinReach(Point[] positions, Point p) {
        for (Point pos : positions) {
            if (pos.distance(p) <= config.powerupSearchRadiusPx()) return true;
        }
        return false;
    }

    private boolean weCanWin(Point p, Point[] positions, List<Point> opponents) {
        double ourBest = Double.MAX_VALUE;
        for (Point pos : positions) ourBest = Math.min(ourBest, powerupNav.getPathDistance(pos, p));
        if (ourBest >= Double.MAX_VALUE) return false;

        double oppBest = Double.MAX_VALUE;
        for (Point opp : opponents) oppBest = Math.min(oppBest, opp.distance(p));
        // opponents judged by straight line (we can't path them) — conservative, underestimates their travel.
        return ourBest <= oppBest * config.raceMargin();
    }

    private double powerupValue(GameState state, Point p, int myPlayerNumber) {
        PowerupType type = state.activePowerups().get(p);
        if (type == PowerupType.RAIN) return config.rainValue();
        // BOMB: worth more where there's paint value to claim (enemy turf).
        return config.bombBaseValue()
                + config.bombTurfScale() * TerritoryUtils.cellPaintValue(state, p.x, p.y, myPlayerNumber);
    }
}
