package lezhor.htw.zebrakit.nav;

import java.awt.Point;
import java.util.List;

public interface Navigator {
    /**
     * Initializes the navigator's internal map using the board's seed.
     */
    void initialize(long seed);

    /**
     * Calculates the full path from start to end.
     * Returns null or empty list if start and end are disconnected.
     */
    List<Point> findPath(Point start, Point end);

    /**
     * Helper to get the vector direction (dx, dy) the bot should face at currentPos
     * to follow the path towards targetPos. Returns a zero vector {0.0, 0.0} if unreachable.
     */
    double[] getNextMoveDirection(Point currentPos, Point targetPos);
}
