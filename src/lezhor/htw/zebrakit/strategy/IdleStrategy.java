package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

/** Freezes all 3 bots in place. Useful as a baseline / no-op opponent. */
public class IdleStrategy implements Strategy {
    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        return new Vector2[]{Vector2.ZERO, Vector2.ZERO, Vector2.ZERO};
    }
}
