package lezhor.htw.zebrakit.runtime;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.strategy.Strategy;

/** The one shared per-tick loop: refresh state, ask the strategy for directions, send them, sleep. */
public class GameLoop {
    private final NetworkClient client;
    private final GameState state;
    private final BotContext[] bots;
    private final Strategy strategy;
    private final long tickSleepMs;

    public GameLoop(NetworkClient client, GameState state, BotContext[] bots, Strategy strategy, long tickSleepMs) {
        this.client = client;
        this.state = state;
        this.bots = bots;
        this.strategy = strategy;
        this.tickSleepMs = tickSleepMs;
    }

    public void run() {
        while (state.isAlive()) {
            state.refresh();

            Vector2[] directions = strategy.decide(state, bots);
            for (int i = 0; i < bots.length; i++) {
                Vector2 dir = directions[i];
                client.setMoveDirection(i, (float) dir.x(), (float) dir.y());
            }

            try {
                Thread.sleep(tickSleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
