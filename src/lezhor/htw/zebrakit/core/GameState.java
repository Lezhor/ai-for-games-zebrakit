package lezhor.htw.zebrakit.core;

import lenz.htw.zebrakit.PowerupType;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.net.NetworkClient;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-tick facade over {@link NetworkClient}: drains queued updates and keeps
 * every player's bot positions and the board's active powerups up to date, so
 * strategies don't each reimplement the same polling/bookkeeping loop. The
 * update stream carries all three players' bots; we keep the opponents' too
 * (for crowding-avoidance and powerup races), not just our own.
 */
public class GameState {
    private static final double POWERUP_PICKUP_RADIUS = 15.0;
    public static final int PLAYER_COUNT = 3;

    private final NetworkClient client;
    private final int myPlayerNumber;
    private final Point[][] botPositions = new Point[PLAYER_COUNT][BotRoles.BOT_COUNT];
    private final PowerupTracker powerupTracker = new PowerupTracker();

    public GameState(NetworkClient client) {
        this.client = client;
        this.myPlayerNumber = client.getMyPlayerNumber();
        for (int p = 0; p < PLAYER_COUNT; p++) {
            for (int b = 0; b < BotRoles.BOT_COUNT; b++) {
                botPositions[p][b] = new Point(512, 512); // fallback until first update
            }
        }
    }

    /** Drains all pending updates from the client and applies them to internal state. Call once per tick. */
    public void refresh() {
        Update update;
        while ((update = client.pullNextUpdate()) != null) {
            PowerupType type = update.type;
            if (type == null) {
                if (update.player >= 0 && update.player < PLAYER_COUNT
                        && update.bot >= 0 && update.bot < BotRoles.BOT_COUNT) {
                    botPositions[update.player][update.bot].setLocation(update.x, update.y);
                }
            } else if (update.player == -1 && update.bot == -1) {
                powerupTracker.onSpawn(new Point(update.x, update.y), type);
            } else {
                powerupTracker.onPickup(new Point(update.x, update.y), POWERUP_PICKUP_RADIUS);
            }
        }
    }

    public int myPlayerNumber() {
        return myPlayerNumber;
    }

    /** Position of one of our own bots (defensive copy). */
    public Point botPosition(int botIndex) {
        return new Point(botPositions[myPlayerNumber][botIndex]);
    }

    /** Position of any player's bot (defensive copy). */
    public Point botPosition(int player, int botIndex) {
        return new Point(botPositions[player][botIndex]);
    }

    /** Every opponent bot's position (all players except ours), as fresh copies. */
    public List<Point> opponentBotPositions() {
        List<Point> result = new ArrayList<>((PLAYER_COUNT - 1) * BotRoles.BOT_COUNT);
        for (int p = 0; p < PLAYER_COUNT; p++) {
            if (p == myPlayerNumber) continue;
            for (int b = 0; b < BotRoles.BOT_COUNT; b++) {
                result.add(new Point(botPositions[p][b]));
            }
        }
        return result;
    }

    public Map<Point, PowerupType> activePowerups() {
        return powerupTracker.active();
    }

    public boolean isWalkable(int x, int y) {
        return client.isWalkable(x, y);
    }

    public int getBoard(int x, int y) {
        return client.getBoard(x, y);
    }

    public long getScore(int player) {
        return client.getScore(player);
    }

    public boolean isAlive() {
        return client.isAlive();
    }
}
