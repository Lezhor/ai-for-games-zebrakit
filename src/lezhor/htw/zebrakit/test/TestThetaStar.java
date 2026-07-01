package lezhor.htw.zebrakit.test;

import java.awt.Point;
import java.util.List;
import lezhor.htw.zebrakit.nav.ThetaStarNavigator;

public class TestThetaStar {
    public static void main(String[] args) {
        long seed = 42L;
        System.out.println("Initializing ThetaStarNavigator with seed " + seed + "...");
        long initStart = System.nanoTime();
        ThetaStarNavigator nav = new ThetaStarNavigator(8, 2);
        nav.initialize(seed);
        long initEnd = System.nanoTime();
        System.out.println("Initialization took: " + ((initEnd - initStart) / 1000000) + " ms");

        // Test 1: Straight clear path (e.g. through the middle if open, or somewhere safe)
        // Let's test a known path. The center is blocked (82 radius), but outside is open.
        // (150, 150) to (200, 200) should be open and quick
        System.out.println("\nTesting Path 1: (150, 150) to (800, 800)");
        Point p1 = new Point(150, 150);
        Point p2 = new Point(800, 800);
        
        long pathStart = System.nanoTime();
        List<Point> path = nav.findPath(p1, p2);
        long pathEnd = System.nanoTime();
        System.out.println("Path calculation took: " + ((pathEnd - pathStart) / 1000000.0) + " ms");
        if (path != null) {
            System.out.println("Path found! Steps: " + path.size());
            for (int i = 0; i < Math.min(5, path.size()); i++) {
                System.out.println("  Step " + i + ": " + path.get(i));
            }
            if (path.size() > 5) System.out.println("  ...");
        } else {
            System.out.println("No path found (unreachable).");
        }

        // Test 2: Point completely inside an obstacle (e.g., center 512, 512)
        System.out.println("\nTesting Path 2: Inside Obstacle (512, 512) to (150, 150)");
        Point p3 = new Point(512, 512);
        pathStart = System.nanoTime();
        List<Point> path2 = nav.findPath(p3, p1);
        pathEnd = System.nanoTime();
        System.out.println("Path calculation took: " + ((pathEnd - pathStart) / 1000000.0) + " ms");
        if (path2 == null) {
            System.out.println("Correctly identified as unreachable.");
        } else {
            System.out.println("ERROR: Found a path from inside an obstacle!");
        }
    }
}
