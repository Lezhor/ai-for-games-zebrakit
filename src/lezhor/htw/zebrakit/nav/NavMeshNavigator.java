package lezhor.htw.zebrakit.nav;

import java.awt.Point;
import java.util.List;

public class NavMeshNavigator implements Navigator {

    @Override
    public void initialize(long seed) {
        // TODO: Extract polygon outlines from the grid.
        // TODO: Triangulate walkable space using Constrained Delaunay Triangulation.
        // TODO: Merge triangles into convex polygons (Hertel-Mehlhorn).
        System.out.println("NavMeshNavigator initialization (seed) is currently a stub.");
    }

    @Override
    public void initialize(lenz.htw.zebrakit.net.NetworkClient client) {
        // TODO: Extract polygon outlines using the live network client map.
        System.out.println("NavMeshNavigator initialization (client) is currently a stub.");
    }

    @Override
    public List<Point> findPath(Point start, Point end) {
        // TODO: A* on NavMesh polygon graph + Funnel Algorithm
        System.out.println("NavMeshNavigator.findPath is currently a stub.");
        return null;
    }

    @Override
    public double getPathDistance(Point start, Point end) {
        System.out.println("NavMeshNavigator.getPathDistance is currently a stub.");
        return Double.MAX_VALUE;
    }

    @Override
    public double[] getNextMoveDirection(Point currentPos, Point targetPos) {
        List<Point> path = findPath(currentPos, targetPos);
        if (path == null || path.size() < 2) {
            return new double[]{0.0, 0.0};
        }
        Point nextPoint = path.get(1);
        double dx = nextPoint.x - currentPos.x;
        double dy = nextPoint.y - currentPos.y;
        return new double[]{dx, dy};
    }
}
