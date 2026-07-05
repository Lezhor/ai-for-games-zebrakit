package lezhor.htw.zebrakit.core;

/** Pure data: the fixed, server-defined abilities of one bot. See {@link BotRoles}. */
public record BotContext(int botIndex, double speed, int paintRadius) {
}
