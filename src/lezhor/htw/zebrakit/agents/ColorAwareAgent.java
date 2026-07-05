package lezhor.htw.zebrakit.agents;

import lenz.htw.zebrakit.PowerupType;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.nav.ColorAStarNavigator;
import lezhor.htw.zebrakit.nav.Navigator;
import lezhor.htw.zebrakit.nav.ThetaStarNavigator;

import java.awt.Point;
import java.util.*;

public class ColorAwareAgent {

    private static final int MAP_SIZE = 1024;
    private static final int GRID_SIZE = 64;
    private static final int CELL_PIXELS = MAP_SIZE / GRID_SIZE;

    private static final long TARGET_REFRESH_MS = 2000;

    // Alphas for bot 0, 1, 2
    private static final double[] ALPHA = {0.01, 0.03, 0.06};

    // Approximate radii for bot 0, 1, 2
    private static final int[] BOT_RADII = {35, 20, 10};

    private static class CellUtility implements Comparable<CellUtility> {
        Point p;
        double utility;

        CellUtility(Point p, double utility) {
            this.p = p;
            this.utility = utility;
        }

        @Override
        public int compareTo(CellUtility o) {
            return Double.compare(o.utility, this.utility); // Descending
        }
    }

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "ColorAwareAgent";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        NetworkClient client = new NetworkClient(host, name, "A* Color Strike!");

        int myPlayerNumber = client.getMyPlayerNumber();

        Navigator geomNavigator = new ThetaStarNavigator(8, 2);
        geomNavigator.initialize(client);

        Navigator colorNavigator = new ColorAStarNavigator(8, 2);
        colorNavigator.initialize(client);

        System.out.println("Navigators initialized");

        Map<Point, PowerupType> activePowerups = new HashMap<>();
        Point[] botPositions = new Point[3];
        Point[] botTargets = new Point[3];
        long[] targetStartTimes = new long[3];

        double[][] influenceMap = new double[GRID_SIZE][GRID_SIZE];
        boolean influenceMapUpdatedThisFrame = false;

        Random rand = new Random();

        for (int i = 0; i < 3; i++) {
            botPositions[i] = new Point(512, 512); // Fallback
        }

        while (client.isAlive()) {
            influenceMapUpdatedThisFrame = false;

            Update update;
            while ((update = client.pullNextUpdate()) != null) {
                if (update.player == myPlayerNumber && update.bot >= 0 && update.bot < 3) {
                    botPositions[update.bot].setLocation(update.x, update.y);
                }

                if (update.type != null) {
                    if (update.player == -1 && update.bot == -1) {
                        activePowerups.put(new Point(update.x, update.y), update.type);
                    } else {
                        removeNearestPowerup(activePowerups, new Point(update.x, update.y), 15);
                    }
                }
            }

            for (int bot = 0; bot < 3; bot++) {
                boolean needsTarget = false;

                if (botTargets[bot] == null) {
                    needsTarget = true;
                } else if (isNear(botPositions[bot], botTargets[bot])) {
                    needsTarget = true;
                } else if (System.currentTimeMillis() - targetStartTimes[bot] > TARGET_REFRESH_MS) {
                    needsTarget = true; // Refresh target frequently
                } else if (isTargetPainted(client, botTargets[bot], myPlayerNumber)) {
                    needsTarget = true; // Target no longer valuable
                }

                if (needsTarget) {
                    // Try to get a powerup first using geometric navigator
                    Point powerupTarget = getBestPowerup(bot, botPositions, activePowerups, geomNavigator);
                    if (powerupTarget != null) {
                        botTargets[bot] = powerupTarget;
                        targetStartTimes[bot] = System.currentTimeMillis();
                    } else {
                        // Use influence map
                        if (!influenceMapUpdatedThisFrame) {
                            updateInfluenceMap(client, myPlayerNumber, influenceMap);
                            influenceMapUpdatedThisFrame = true;
                        }

                        Point newTarget = findBestTargetFromInfluence(bot, botPositions, botTargets, influenceMap, client, geomNavigator, rand);
                        botTargets[bot] = newTarget;
                        targetStartTimes[bot] = System.currentTimeMillis();
                    }
                }

                if (botTargets[bot] != null) {
                    // Update bot radius for color sampling
                    if (colorNavigator instanceof ColorAStarNavigator) {
                        ((ColorAStarNavigator) colorNavigator).setBotRadius(BOT_RADII[bot]);
                    }

                    // We use the color-aware navigator to physically move to the target, UNLESS it's a powerup (where we just want the fastest route)
                    Navigator activeNavigator = isPowerupTarget(botTargets[bot], activePowerups) ? geomNavigator : colorNavigator;

                    double[] dir = activeNavigator.getNextMoveDirection(botPositions[bot], botTargets[bot]);
                    if (dir[0] == 0.0 && dir[1] == 0.0 && !isNear(botPositions[bot], botTargets[bot])) {
                        // Unreachable
                        botTargets[bot] = null;
                    } else {
                        client.setMoveDirection(bot, (float) dir[0], (float) dir[1]);
                    }
                } else {
                    // Random fallback
                    client.setMoveDirection(bot, rand.nextFloat() * 2 - 1, rand.nextFloat() * 2 - 1);
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isPowerupTarget(Point target, Map<Point, PowerupType> activePowerups) {
        for (Point p : activePowerups.keySet()) {
            if (p.distance(target) < 15) {
                return true;
            }
        }
        return false;
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
        return (dx * dx + dy * dy) < 400; // 20 pixels radius
    }

    private static boolean isTargetPainted(NetworkClient client, Point target, int myPlayerNumber) {
        int val = client.getBoard(target.x, target.y);
        if (val == 0) return true; // Wall is considered painted/useless

        int r = (val >> 16) & 0xFF;
        int g = (val >> 8) & 0xFF;
        int b = val & 0xFF;

        int myVal = (myPlayerNumber == 0) ? r : ((myPlayerNumber == 1) ? g : b);
        return myVal > 220;
    }

    private static Point getBestPowerup(int bot, Point[] botPositions, Map<Point, PowerupType> activePowerups, Navigator geomNavigator) {
        Point bestPowerup = null;
        double minPathDist = Double.MAX_VALUE;

        for (Map.Entry<Point, PowerupType> entry : activePowerups.entrySet()) {
            if (entry.getValue() == PowerupType.SLOW) continue; // Avoid hazards
            Point p = entry.getKey();

            // Rough distance filter - increased to 500 for more frequent powerup targeting
            if (p.distance(botPositions[bot]) > 500) continue;

            double pathDist = geomNavigator.getPathDistance(botPositions[bot], p);
            if (pathDist < minPathDist) {
                // Check if another bot is closer
                boolean someoneCloser = false;
                for (int other = 0; other < 3; other++) {
                    if (other == bot) continue;
                    if (geomNavigator.getPathDistance(botPositions[other], p) < pathDist) {
                        someoneCloser = true;
                        break;
                    }
                }

                if (!someoneCloser) {
                    minPathDist = pathDist;
                    bestPowerup = p;
                }
            }
        }

        // If it's too far to walk to, even if we are the closest, don't override target unless it's reasonable
        if (minPathDist < 600) {
            return bestPowerup;
        }
        return null;
    }

    private static void updateInfluenceMap(NetworkClient client, int myPlayerNumber, double[][] influenceMap) {
        double[][] cellScores = new double[GRID_SIZE][GRID_SIZE];

        long score0 = client.getScore(0);
        long score1 = client.getScore(1);
        long score2 = client.getScore(2);

        double scoreA = 0, scoreB = 0;
        if (myPlayerNumber == 0) {
            scoreA = score1; scoreB = score2;
        } else if (myPlayerNumber == 1) {
            scoreA = score0; scoreB = score2;
        } else {
            scoreA = score0; scoreB = score1;
        }

        double totalOtherScore = Math.max(1, scoreA + scoreB);
        // Exaggerate the weight of the winning opponent
        double weightA = 1.0 + 2.0 * (scoreA / totalOtherScore);
        double weightB = 1.0 + 2.0 * (scoreB / totalOtherScore);

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gy = 0; gy < GRID_SIZE; gy++) {
                int px = gx * CELL_PIXELS + CELL_PIXELS / 2;
                int py = gy * CELL_PIXELS + CELL_PIXELS / 2;

                int val = client.getBoard(px, py);
                if (val != 0) {
                    int r = (val >> 16) & 0xFF;
                    int g = (val >> 8) & 0xFF;
                    int b = val & 0xFF;

                    int myVal = 0;
                    double otherVal = 0;
                    if (myPlayerNumber == 0) {
                        myVal = r;
                        otherVal = g * weightA + b * weightB;
                    } else if (myPlayerNumber == 1) {
                        myVal = g;
                        otherVal = r * weightA + b * weightB;
                    } else {
                        myVal = b;
                        otherVal = r * weightA + g * weightB;
                    }

                    // Multiply our own potential gain by 2.0 so we strictly prefer
                    // an opponent's color (where we gain points) over white (where our color is already maxed).
                    cellScores[gx][gy] = (255 - myVal) * 2.0 + otherVal;
                } else {
                    cellScores[gx][gy] = 0;
                }
            }
        }

        int kernelRadius = 4;
        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gy = 0; gy < GRID_SIZE; gy++) {
                double sum = 0;
                for (int dx = -kernelRadius; dx <= kernelRadius; dx++) {
                    for (int dy = -kernelRadius; dy <= kernelRadius; dy++) {
                        int nx = gx + dx;
                        int ny = gy + dy;
                        if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE) {
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist <= kernelRadius) {
                                double weight = 1.0 - (dist / (kernelRadius + 1));
                                sum += cellScores[nx][ny] * weight;
                            }
                        }
                    }
                }
                influenceMap[gx][gy] = sum;
            }
        }
    }

    private static Point findBestTargetFromInfluence(int bot, Point[] botPositions, Point[] botTargets, double[][] influenceMap, NetworkClient client, Navigator geomNavigator, Random rand) {
        List<CellUtility> candidates = new ArrayList<>();
        Point myPos = botPositions[bot];
        double myGx = myPos.x / (double)CELL_PIXELS;
        double myGy = myPos.y / (double)CELL_PIXELS;

        double dispersionSigma = 12.0;

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gy = 0; gy < GRID_SIZE; gy++) {
                double influence = influenceMap[gx][gy];
                if (influence <= 0) continue; // Not worth it

                int px = gx * CELL_PIXELS + CELL_PIXELS / 2;
                int py = gy * CELL_PIXELS + CELL_PIXELS / 2;

                if (!client.isWalkable(px, py)) continue;

                double dx = gx - myGx;
                double dy = gy - myGy;
                double distToBot = Math.sqrt(dx * dx + dy * dy);

                double distanceWeight = 1.0 / (1.0 + ALPHA[bot] * distToBot);

                double dispersionWeight = 1.0;
                for (int other = 0; other < 3; other++) {
                    if (other == bot) continue;

                    // Penalty for proximity to other bot's position
                    Point otherPos = botPositions[other];
                    double odx = gx - (otherPos.x / (double)CELL_PIXELS);
                    double ody = gy - (otherPos.y / (double)CELL_PIXELS);
                    double distToOtherPosSq = odx * odx + ody * ody;
                    dispersionWeight *= (1.0 - Math.exp(-distToOtherPosSq / (2.0 * dispersionSigma * dispersionSigma)));

                    // Penalty for proximity to other bot's target
                    Point otherTarget = botTargets[other];
                    if (otherTarget != null) {
                        double tdx = gx - (otherTarget.x / (double)CELL_PIXELS);
                        double tdy = gy - (otherTarget.y / (double)CELL_PIXELS);
                        double distToOtherTargetSq = tdx * tdx + tdy * tdy;
                        dispersionWeight *= (1.0 - Math.exp(-distToOtherTargetSq / (2.0 * dispersionSigma * dispersionSigma)));
                    }
                }

                double utility = influence * distanceWeight * dispersionWeight;
                candidates.add(new CellUtility(new Point(px, py), utility));
            }
        }

        Collections.sort(candidates);

        // Try top 10 reachable targets using geometric navigator
        int limit = Math.min(10, candidates.size());
        for (int i = 0; i < limit; i++) {
            Point candidatePoint = candidates.get(i).p;
            if (geomNavigator.getPathDistance(myPos, candidatePoint) < Double.MAX_VALUE) {
                return candidatePoint;
            }
        }

        // Fallback
        while (true) {
            int rx = rand.nextInt(MAP_SIZE);
            int ry = rand.nextInt(MAP_SIZE);
            if (client.isWalkable(rx, ry)) {
                return new Point(rx, ry);
            }
        }
    }
}
