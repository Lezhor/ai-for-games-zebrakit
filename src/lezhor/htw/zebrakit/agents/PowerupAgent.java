package lezhor.htw.zebrakit.agents;

import lenz.htw.zebrakit.PowerupType;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.nav.Navigator;
import lezhor.htw.zebrakit.nav.ThetaStarNavigator;

import java.awt.Point;
import java.util.*;

public class PowerupAgent {

    // Speeds for bots 0, 1, 2. Used to calculate estimated time-to-reach.
    // TODO: lookup actual speeds
    private static final double[] BOT_SPEEDS = { 2.0, 1.5, 1.0 };

    private static class BotPowerupPair implements Comparable<BotPowerupPair> {
        int bot;
        Point powerup;
        double distance;
        double timeToReach;

        BotPowerupPair(int bot, Point powerup, double distance) {
            this.bot = bot;
            this.powerup = powerup;
            this.distance = distance;
            this.timeToReach = distance / BOT_SPEEDS[bot];
        }

        @Override
        public int compareTo(BotPowerupPair o) {
            return Double.compare(this.timeToReach, o.timeToReach);
        }
    }

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "PowerupHunter";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        NetworkClient client = new NetworkClient(host, name, "yeay");

        int myPlayerNumber = client.getMyPlayerNumber();
        Navigator navigator = new ThetaStarNavigator(8, 2);
        navigator.initialize(client);
        System.out.println("Navigator initialized.");

        Map<Point, PowerupType> activePowerups = new HashMap<>();
        Point[] botPositions = new Point[3];
        Point[] botTargets = new Point[3];
        float[] dx = new float[3];
        float[] dy = new float[3];

        Random rand = new Random();
        for (int i = 0; i < 3; i++) {
            botPositions[i] = new Point(512, 512); // Fallback
            dx[i] = rand.nextFloat() * 2 - 1;
            dy[i] = rand.nextFloat() * 2 - 1;
        }

        while (client.isAlive()) {
            Update update;
            while ((update = client.pullNextUpdate()) != null) {
                // Update bot positions
                if (update.player == myPlayerNumber && update.bot >= 0 && update.bot < 3) {
                    botPositions[update.bot].setLocation(update.x, update.y);
                }

                // Track powerup spawns and collects
                if (update.type != null) {
                    if (update.player == -1 && update.bot == -1) {
                        activePowerups.put(new Point(update.x, update.y), update.type);
                    } else {
                        removeNearestPowerup(activePowerups, new Point(update.x, update.y), 15);
                    }
                }
            }

            // --- 1. Greedy Global Target Assignment ---
            // Calculate true distance from EVERY bot to EVERY powerup
            List<BotPowerupPair> allPairs = new ArrayList<>();
            for (int bot = 0; bot < 3; bot++) {
                for (Map.Entry<Point, PowerupType> entry : activePowerups.entrySet()) {
                    if (entry.getValue() == PowerupType.SLOW) continue;
                    Point p = entry.getKey();
                    double dist = navigator.getPathDistance(botPositions[bot], p);
                    if (dist < Double.MAX_VALUE) { // Reachable
                        allPairs.add(new BotPowerupPair(bot, p, dist));
                    }
                }
            }
            // Sort by shortest true path distance globally
            Collections.sort(allPairs);

            Set<Integer> assignedBots = new HashSet<>();
            Set<Point> assignedPowerups = new HashSet<>();
            for (int bot = 0; bot < 3; bot++) {
                botTargets[bot] = null; // Clear old targets
            }

            // Greedily assign the closest pairings first
            for (BotPowerupPair pair : allPairs) {
                if (!assignedBots.contains(pair.bot) && !assignedPowerups.contains(pair.powerup)) {
                    botTargets[pair.bot] = pair.powerup;
                    assignedBots.add(pair.bot);
                    assignedPowerups.add(pair.powerup);
                }
            }

            // --- 2. Movement Logic ---
            for (int bot = 0; bot < 3; bot++) {
                if (botTargets[bot] != null) {
                    // We have a powerup target, use pathfinder!
                    double[] dir = navigator.getNextMoveDirection(botPositions[bot], botTargets[bot]);
                    if (dir[0] == 0.0 && dir[1] == 0.0 && !isNear(botPositions[bot], botTargets[bot])) {
                        // Unreachable due to some edge case, fall back to random walking
                        botTargets[bot] = null;
                    } else {
                        client.setMoveDirection(bot, (float) dir[0], (float) dir[1]);
                    }
                }

                if (botTargets[bot] == null) {
                    // No powerup assigned. Default to RandomAgent's straight line bouncing!
                    float len = (float) Math.sqrt(dx[bot] * dx[bot] + dy[bot] * dy[bot]);
                    if (len == 0) {
                        dx[bot] = 1;
                        len = 1;
                    }
                    float nx = dx[bot] / len;
                    float ny = dy[bot] / len;

                    boolean willHit = false;
                    for (int step = 5; step <= 25; step += 5) {
                        int checkX = botPositions[bot].x + (int) (nx * step);
                        int checkY = botPositions[bot].y + (int) (ny * step);

                        if (!client.isWalkable(checkX, checkY)) {
                            willHit = true;
                            break;
                        }
                    }

                    if (willHit) {
                        dx[bot] = rand.nextFloat() * 2 - 1;
                        dy[bot] = rand.nextFloat() * 2 - 1;
                    }
                    client.setMoveDirection(bot, dx[bot], dy[bot]);
                }
            }

            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void removeNearestPowerup(Map<Point, PowerupType> activePowerups, Point loc, double radius) {
        Point toRemove = null;
        double minD = Double.MAX_VALUE;
        for (Point p : activePowerups.keySet()) {
            double d = p.distance(loc);
            if (d <= radius && d < minD) {
                minD = d;
                toRemove = p;
            }
        }
        if (toRemove != null) {
            activePowerups.remove(toRemove);
        }
    }

    private static boolean isNear(Point p1, Point p2) {
        if (p1 == null || p2 == null) return false;
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return (dx * dx + dy * dy) < 400;
    }
}
