package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;
import lezhor.htw.zebrakit.strategy.support.WanderMovement;

/** Bounces each bot off walls in a straight line, picking a new random direction on collision. */
public class RandomWalkStrategy implements Strategy {
    private final WanderMovement wander = new WanderMovement();

    @Override
    public Vector2[] decide(GameState state, BotContext[] bots) {
        Vector2[] result = new Vector2[BotRoles.BOT_COUNT];
        for (int i = 0; i < BotRoles.BOT_COUNT; i++) {
            result[i] = wander.next(state, i, state.botPosition(i));
        }
        return result;
    }
}
