package lezhor.htw.zebrakit.agents;

import lenz.htw.zebrakit.net.NetworkClient;
import lenz.htw.zebrakit.Update;
import lezhor.htw.zebrakit.nav.Navigator;
import lezhor.htw.zebrakit.nav.ThetaStarNavigator;

import java.awt.Point;
import java.util.Random;

public class RandomTargetAgent {

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "RandomTargetAgent";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        NetworkClient client = new NetworkClient(host, name, "Yeay!");

        int myPlayerNumber = client.getMyPlayerNumber();
        Random rand = new Random();

        // 1. Initialize Navigator using the live client
        System.out.println("Initializing ThetaStar Navigator...");
        Navigator navigator = new ThetaStarNavigator(4, 2);
        navigator.initialize(client);
        System.out.println("Navigator ready. Game loop started.");

        Point[] botPositions = new Point[3];
        Point[] botTargets = new Point[3];
        long lastTargetTime = 0;

        for (int i = 0; i < 3; i++) {
            botPositions[i] = new Point(512, 512); // Fallback until first update
            botTargets[i] = getRandomWalkableTarget(client, rand);
        }

        while (client.isAlive()) {
            boolean timeToSwitch = false;
            long now = System.currentTimeMillis();
            if (now - lastTargetTime > 10000) { // 10 seconds
                lastTargetTime = now;
                timeToSwitch = true;
                System.out.println("10 seconds elapsed. Picking new targets!");
            }

            Update update;
            // Process all game events currently in the queue
            while ((update = client.pullNextUpdate()) != null) {
                // Keep track of where our bots actually are
                if (update.player == myPlayerNumber && update.bot >= 0 && update.bot < 3) {
                    botPositions[update.bot].setLocation(update.x, update.y);
                }
            }

            // Command bots towards their targets
            for (int bot = 0; bot < 3; bot++) {
                if (timeToSwitch || botTargets[bot] == null || isNear(botPositions[bot], botTargets[bot])) {
                    botTargets[bot] = getRandomWalkableTarget(client, rand);
                }

                // Get continuous path direction
                double[] dir = navigator.getNextMoveDirection(botPositions[bot], botTargets[bot]);

                // If pathfinder returns (0,0) but we are not near the target, it means
                // the target is completely unreachable (disconnected island). Pick a new one!
                if (dir[0] == 0.0 && dir[1] == 0.0 && !isNear(botPositions[bot], botTargets[bot])) {
                    botTargets[bot] = getRandomWalkableTarget(client, rand);
                } else {
                    // Send direction to server
                    client.setMoveDirection(bot, (float) dir[0], (float) dir[1]);
                }
            }

            try {
                // Sleep slightly so we don't burn CPU in an infinite loop
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static Point getRandomWalkableTarget(NetworkClient client, Random rand) {
        while (true) {
            int x = rand.nextInt(1024);
            int y = rand.nextInt(1024);
            if (client.isWalkable(x, y)) {
                return new Point(x, y);
            }
        }
    }

    private static boolean isNear(Point p1, Point p2) {
        if (p1 == null || p2 == null) return false;
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return (dx * dx + dy * dy) < 400; // within 20 pixels radius
    }
}
