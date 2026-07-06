package lezhor.htw.zebrakit.core;

import lenz.htw.zebrakit.net.NetworkClient;

/**
 * Authoritative, server-defined per-bot-index constants (mirrored from the
 * decompiled server: speed, paint radius and relative paint strength are
 * fixed by bot index, not chosen by any strategy).
 *
 * bot0: fast, wide radius, weak/transparent paint.
 * bot1: slow, narrow radius, strong/opaque paint.
 * bot2: slowest, medium radius, medium paint.
 */
public final class BotRoles {
    public static final int BOT_COUNT = 3;

    public static final double[] SPEED = {6.3, 3.0, 2.0};
    public static final int[] PAINT_RADIUS = {40, 15, 30};

    private BotRoles() {
    }

    public static BotContext[] buildContexts(NetworkClient client) {
        BotContext[] bots = new BotContext[BOT_COUNT];
        for (int i = 0; i < BOT_COUNT; i++) {
            int radius = client != null ? client.getInfluenceRadiusForBot(i) : PAINT_RADIUS[i];
            bots[i] = new BotContext(i, SPEED[i], radius);
        }
        return bots;
    }
}
