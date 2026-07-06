package lezhor.htw.zebrakit.core;

import lenz.htw.zebrakit.PowerupType;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.net.NetworkClient;

import java.awt.Point;
import java.util.Map;

/**
 * Per-tick facade over {@link NetworkClient}: drains queued updates and keeps
 * this player's bot positions and the board's active powerups up to date, so
 * strategies don't each reimplement the same polling/bookkeeping loop.
 */
public class GameState {
    private static final double POWERUP_PICKUP_RADIUS = 15.0;

    private final NetworkClient client;
    private final int myPlayerNumber;
    private final Point[] botPositions = new Point[BotRoles.BOT_COUNT];
    private final PowerupTracker powerupTracker = new PowerupTracker();

    public GameState(NetworkClient client) {
        this.client = client;
        this.myPlayerNumber = client.getMyPlayerNumber();
        for (int i = 0; i < botPositions.length; i++) {
            botPositions[i] = new Point(512, 512); // fallback until first update
        }
    }

    /** Drains all pending updates from the client and applies them to internal state. Call once per tick. */
    public void refresh() {
        Update update;
        while ((update = client.pullNextUpdate()) != null) {
            PowerupType type = update.type;
            if (type == null) {
                if (update.player == myPlayerNumber && update.bot >= 0 && update.bot < botPositions.length) {
                    botPositions[update.bot].setLocation(update.x, update.y);
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

    public Point botPosition(int botIndex) {
        return new Point(botPositions[botIndex]);
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
