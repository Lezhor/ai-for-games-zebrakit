package lezhor.htw.zebrakit.movement.steering;

import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;

/**
 * Samples nearby walkable cells in a ring around the bot and biases toward
 * whichever direction has the highest {@link TerritoryUtils#cellPaintValue}
 * — our own gain plus stripped opponent value, weighted toward whoever is
 * leading — so a bot heading toward a waypoint drifts through the most
 * valuable nearby space along the way (including white/unclaimed cells,
 * which strip full value from both opponents even though they give us no
 * direct gain).
 */
public class SeekPaintValueBehavior implements SteeringBehavior {
    private final int sampleRadiusPx;
    private final int sampleCount;

    public SeekPaintValueBehavior(int sampleRadiusPx) {
        this(sampleRadiusPx, 8);
    }

    public SeekPaintValueBehavior(int sampleRadiusPx, int sampleCount) {
        this.sampleRadiusPx = sampleRadiusPx;
        this.sampleCount = sampleCount;
    }

    @Override
    public Vector2 steer(GameState state, BotContext self, Point currentPos, Point target) {
        int myPlayerNumber = state.myPlayerNumber();
        Vector2 best = Vector2.ZERO;
        double bestValue = -1;

        for (int i = 0; i < sampleCount; i++) {
            double angle = 2 * Math.PI * i / sampleCount;
            double dirX = Math.cos(angle);
            double dirY = Math.sin(angle);
            int sx = currentPos.x + (int) (dirX * sampleRadiusPx);
            int sy = currentPos.y + (int) (dirY * sampleRadiusPx);

            if (!state.isWalkable(sx, sy)) continue;
            double value = TerritoryUtils.cellPaintValue(state, sx, sy, myPlayerNumber);
            if (value > bestValue) {
                bestValue = value;
                best = new Vector2(dirX, dirY);
            }
        }
        return best;
    }
}
