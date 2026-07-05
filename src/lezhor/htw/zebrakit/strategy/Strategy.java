package lezhor.htw.zebrakit.strategy;

import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

/**
 * Controls all 3 of a player's bots together, one instance per player. Bots
 * are differentiated INSIDE decide() using each BotContext's speed/paintRadius
 * (e.g. send the fast bot after powerups, keep the strong-paint bot on
 * territory) — there is no per-bot Strategy instance, so coordination
 * (spreading out, avoiding double-claiming a target) is just ordinary code
 * within one decide() call.
 */
public interface Strategy {
    /** Called once per tick. Returns one direction per bot index (0..2). */
    Vector2[] decide(GameState state, BotContext[] bots);
}
