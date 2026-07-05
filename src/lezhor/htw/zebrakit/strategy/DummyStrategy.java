package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

/**
 * New-architecture equivalent of the original {@code agents.Dummy} reference
 * example (kept unchanged elsewhere): bot0 always moves straight down, bots
 * 1 and 2 are left stationary.
 */
public class DummyStrategy implements Strategy {
    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        return new Vector2[]{new Vector2(0, 1), Vector2.ZERO, Vector2.ZERO};
    }
}
