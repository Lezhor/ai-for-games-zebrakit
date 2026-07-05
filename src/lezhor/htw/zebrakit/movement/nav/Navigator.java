package lezhor.htw.zebrakit.movement.nav;

import lezhor.htw.zebrakit.movement.MovementProvider;

import java.awt.Point;
import java.util.List;

public interface Navigator extends MovementProvider {
    /**
     * Calculates the full path from start to end.
     * Returns null or empty list if start and end are disconnected.
     */
    List<Point> findPath(Point start, Point end);

    /**
     * Calculates the actual walking distance of the shortest path from start to end.
     * Returns Double.MAX_VALUE if start and end are disconnected.
     */
    double getPathDistance(Point start, Point end);

    @Override
    default boolean isReachable(Point currentPos, Point targetPos) {
        return getPathDistance(currentPos, targetPos) < Double.MAX_VALUE;
    }
}
