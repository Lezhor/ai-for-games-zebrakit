package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;

import java.awt.Point;
import java.util.List;

/**
 * An on-demand ("lambda") evaluator — no grid: it scores a pixel purely by how far
 * it is from every <em>other</em> bot, so a bot is pulled toward open space and away
 * from crowding (its own teammates and the enemies). For each other bot it adds the
 * square root of the Euclidean distance (a gently saturating reward, so being a bit
 * farther always helps but with diminishing returns), weighted by whether that bot is
 * on our team or an opponent's:
 * <pre>value = Σ_teammate OWN_TEAM_WEIGHT·√dist + Σ_enemy ENEMY_TEAM_WEIGHT·√dist</pre>
 * The bot being scored is excluded. Values are non-negative (distances are), matching
 * the evaluator convention. Bot positions are snapshotted once per tick in
 * {@link #update} so the per-pixel {@link #valueAt} allocates nothing.
 */
public final class BotDistanceEvaluator implements PixelEvaluator {
    private double outerWeight;
    private double ownTeamWeight;
    private double enemyTeamWeight;

    private final Point[] ownTeam = new Point[BotRoles.BOT_COUNT];
    private Point[] enemyBots = new Point[0];

    @Override
    public void init(GameState state, BresenhamConfig config) {
        this.outerWeight = config.botDistanceWeight();
        this.ownTeamWeight = config.ownTeamWeight();
        this.enemyTeamWeight = config.enemyTeamWeight();
    }

    @Override
    public void update(GameState state, int myPlayerNumber, PlayerWeights weights) {
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            ownTeam[i] = state.botPosition(myPlayerNumber, i);
        }
        List<Point> opponents = state.opponentBotPositions();
        enemyBots = opponents.toArray(new Point[0]);
    }

    @Override
    public double valueAt(int x, int y, GameState state, BotContext bot) {
        int selfIndex = bot.botIndex();
        double total = 0;
        for (int i = 0; i < ownTeam.length; i++) {
            if (i == selfIndex) continue; // exclude the bot we're scoring for
            total += ownTeamWeight * rootDistance(x, y, ownTeam[i]);
        }
        for (Point enemy : enemyBots) {
            total += enemyTeamWeight * rootDistance(x, y, enemy);
        }
        return total;
    }

    /** Square root of the Euclidean distance from (x,y) to p. */
    private static double rootDistance(int x, int y, Point p) {
        double dx = x - p.x;
        double dy = y - p.y;
        return Math.sqrt(Math.sqrt(dx * dx + dy * dy));
    }

    @Override
    public double weight() {
        return outerWeight;
    }
}
