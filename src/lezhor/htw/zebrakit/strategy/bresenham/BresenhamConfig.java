package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.strategy.support.IniConfig;

/**
 * Typed, immutable tuning struct for {@link lezhor.htw.zebrakit.strategy.BresenhamStrategy},
 * built once from an optional {@link IniConfig}. Every field's default lives here in
 * {@link #from(IniConfig)} as a literal, so the strategy runs fully without a config file and
 * the {@code config/bresenham.ini} shipped alongside is just those same defaults written out.
 *
 * <p>The config is organised into the same {@code [Section]}s as the ini file: {@code General}
 * (weights shared across evaluators), {@code Raycast}, {@code Walls}, {@code PaintValue},
 * {@code BotDistance}, and {@code Powerups}. Evaluators receive this whole struct at
 * {@code init(...)} and read the fields they need.
 */
public record BresenhamConfig(
        // [General] — shared across evaluators
        double ownWeight,
        double enemyBase,
        double leaderScale,
        // [Raycast]
        int directionCount,
        double lineLength,
        double falloffSlope,
        double speedScaling,
        int recheckIntervalTicks,
        // [Walls]
        int wallGridScale,
        double wallRadiusPx,
        double minWallDistancePx,
        // [PaintValue]
        double paintValueWeight,
        int paintValueGridScale,
        int paintValueBlurIterations,
        // [BotDistance]
        double botDistanceWeight,
        double ownTeamWeight,
        double enemyTeamWeight,
        // [Powerups]
        boolean powerupsEnabled,
        double powerupSearchRadiusPx,
        double powerupMaxPathDist,
        double raceMargin,
        double bombBaseValue,
        double bombTurfScale,
        double rainValue) {

    private static final String GENERAL = "General";
    private static final String RAYCAST = "Raycast";
    private static final String WALLS = "Walls";
    private static final String PAINT_VALUE = "PaintValue";
    private static final String BOT_DISTANCE = "BotDistance";
    private static final String POWERUPS = "Powerups";

    /** Builds the config from an ini file, or returns all-defaults when {@code config} is {@code null}. */
    public static BresenhamConfig from(IniConfig config) {
        if (config != null) config.requireType("Bresenham");
        return new BresenhamConfig(
                d(config, GENERAL, "OWN_WEIGHT", 2.0),
                d(config, GENERAL, "ENEMY_BASE", 1.0),
                d(config, GENERAL, "LEADER_SCALE", 2.0),

                i(config, RAYCAST, "DIRECTION_COUNT", 8),
                d(config, RAYCAST, "LINE_LENGTH", 200),
                d(config, RAYCAST, "FALLOFF_SLOPE", 0.003),
                d(config, RAYCAST, "SPEED_SCALING", 1.0),
                i(config, RAYCAST, "RECHECK_INTERVAL_TICKS", 8),

                i(config, WALLS, "WALL_GRID_SCALE", 4),
                d(config, WALLS, "WALL_RADIUS_PX", 8),
                d(config, WALLS, "MIN_WALL_DISTANCE_PX", 30),

                d(config, PAINT_VALUE, "WEIGHT", 1.0),
                i(config, PAINT_VALUE, "GRID_SCALE", 8),
                i(config, PAINT_VALUE, "BLUR_ITERATIONS", 2),

                d(config, BOT_DISTANCE, "WEIGHT", 1.0),
                d(config, BOT_DISTANCE, "OWN_TEAM_WEIGHT", 1.0),
                d(config, BOT_DISTANCE, "ENEMY_TEAM_WEIGHT", 1.0),

                b(config, POWERUPS, "ENABLED", true),
                d(config, POWERUPS, "SEARCH_RADIUS_PX", 700),
                d(config, POWERUPS, "MAX_PATH_DIST", 700),
                d(config, POWERUPS, "RACE_MARGIN", 1.0),
                d(config, POWERUPS, "BOMB_BASE_VALUE", 400),
                d(config, POWERUPS, "BOMB_TURF_SCALE", 0.5),
                d(config, POWERUPS, "RAIN_VALUE", 500));
    }

    private static double d(IniConfig c, String section, String key, double fallback) {
        return c == null ? fallback : c.getDouble(section, key, fallback);
    }

    private static int i(IniConfig c, String section, String key, int fallback) {
        return c == null ? fallback : c.getInt(section, key, fallback);
    }

    private static boolean b(IniConfig c, String section, String key, boolean fallback) {
        return c == null ? fallback : c.getBoolean(section, key, fallback);
    }
}
