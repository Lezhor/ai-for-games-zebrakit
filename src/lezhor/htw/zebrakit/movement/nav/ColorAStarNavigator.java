package lezhor.htw.zebrakit.movement.nav;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.analysis.OpponentWeights;
import lezhor.htw.zebrakit.analysis.TerritoryUtils;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.*;

public class ColorAStarNavigator implements Navigator {
    private static final int MAP_SIZE = BoardConstants.MAP_SIZE;
    private final int inflationRadius;
    private final int scale;
    private final int scaledSize;

    private boolean[][] grid;
    private NetworkClient client;
    private int myPlayerNumber;
    private int currentBotRadius = 15; // Default radius

    public static final double MAX_PATH_PENALTY = 15.0;

    private static class Node {
        int x, y;
        double g, f;
        Node parent;
        boolean closed;

        Node(int x, int y, double g, double f, Node parent) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }

    public ColorAStarNavigator() {
        this(8, 2);
    }

    public ColorAStarNavigator(int inflationRadius, int scale) {
        this.inflationRadius = inflationRadius;
        this.scale = scale;
        this.scaledSize = MAP_SIZE / scale;
    }

    @Override
    public void initialize(long seed) {
        lenz.htw.zebrakit.e board = new lenz.htw.zebrakit.e(seed);
        initGridAndInflate((x, y) -> board.a(x, y) != 0);
    }

    @Override
    public void initialize(NetworkClient client) {
        this.client = client;
        this.myPlayerNumber = client.getMyPlayerNumber();
        initGridAndInflate(client::isWalkable);
    }

    public interface WalkableChecker {
        boolean isWalkable(int x, int y);
    }

    private void initGridAndInflate(WalkableChecker checker) {
        grid = new boolean[scaledSize][scaledSize];
        boolean[][] rawWalkable = new boolean[scaledSize][scaledSize];

        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {
                int origX = x * scale + scale / 2;
                int origY = y * scale + scale / 2;
                rawWalkable[x][y] = checker.isWalkable(origX, origY);
            }
        }

        int scaledInflation = Math.max(1, inflationRadius / scale);

        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {
                grid[x][y] = true;
                if (!rawWalkable[x][y]) continue;

                boolean tooClose = false;
                for (int dx = -scaledInflation; dx <= scaledInflation; dx++) {
                    for (int dy = -scaledInflation; dy <= scaledInflation; dy++) {
                        if (dx * dx + dy * dy <= scaledInflation * scaledInflation) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (isValid(nx, ny) && !rawWalkable[nx][ny]) {
                                tooClose = true;
                                break;
                            }
                        }
                    }
                    if (tooClose) break;
                }
                grid[x][y] = !tooClose;
            }
        }
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < scaledSize && y >= 0 && y < scaledSize;
    }

    private double heuristic(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public List<Point> findPath(Point start, Point end) {
        if (grid == null) return null;

        Point sStart = new Point(start.x / scale, start.y / scale);
        Point sEnd = new Point(end.x / scale, end.y / scale);

        if (!isValid(sStart.x, sStart.y) || !grid[sStart.x][sStart.y] ||
            !isValid(sEnd.x, sEnd.y) || !grid[sEnd.x][sEnd.y]) {
            return null;
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Point, Node> allNodes = new HashMap<>();

        Node startNode = new Node(sStart.x, sStart.y, 0, heuristic(sStart, sEnd), null);
        open.add(startNode);
        allNodes.put(sStart, startNode);

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        double[] baseCost = {1, 1, 1, 1, 1.414, 1.414, 1.414, 1.414};

        Node targetNode = null;

        int opponentA = TerritoryUtils.otherPlayerA(myPlayerNumber);
        int opponentB = TerritoryUtils.otherPlayerB(myPlayerNumber);
        OpponentWeights.Weights weights = OpponentWeights.compute(
                client != null ? client.getScore(opponentA) : 0,
                client != null ? client.getScore(opponentB) : 0);

        while (!open.isEmpty()) {
            Node current = open.poll();
            current.closed = true;

            if (current.x == sEnd.x && current.y == sEnd.y) {
                targetNode = current;
                break;
            }

            for (int i = 0; i < 8; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];

                if (!isValid(nx, ny) || !grid[nx][ny]) continue;

                Point nPoint = new Point(nx, ny);
                Node neighbor = allNodes.get(nPoint);
                if (neighbor == null) {
                    neighbor = new Node(nx, ny, Double.MAX_VALUE, 0, null);
                    allNodes.put(nPoint, neighbor);
                }

                if (neighbor.closed) continue;

                double weight = getColorWeight(nx * scale + scale / 2, ny * scale + scale / 2, opponentA, opponentB, weights);
                double moveCost = baseCost[i] * weight;

                double newG = current.g + moveCost;
                if (newG < neighbor.g) {
                    neighbor.g = newG;
                    neighbor.f = newG + heuristic(nPoint, sEnd);
                    neighbor.parent = current;
                    open.remove(neighbor);
                    open.add(neighbor);
                }
            }
        }

        if (targetNode == null) return null;

        List<Point> path = new ArrayList<>();
        Node curr = targetNode;
        while (curr != null) {
            path.add(new Point(curr.x * scale + scale / 2, curr.y * scale + scale / 2));
            curr = curr.parent;
        }
        Collections.reverse(path);

        path.set(0, start);
        path.set(path.size() - 1, end);

        return path;
    }

    public void setBotRadius(int radius) {
        this.currentBotRadius = radius;
    }

    private double getColorWeight(int px, int py, int opponentA, int opponentB, OpponentWeights.Weights weights) {
        if (client == null) return 1.0;

        // Treat center pixel wall as strict penalty
        if (client.getBoard(px, py) == 0) return 1.0 + MAX_PATH_PENALTY;

        long totalMyVal = 0;
        long totalValA = 0;
        long totalValB = 0;
        int samples = 0;

        int step = currentBotRadius;
        for (int dx = -step; dx <= step; dx += step) {
            for (int dy = -step; dy <= step; dy += step) {
                int val = client.getBoard(px + dx, py + dy);
                if (val == 0) continue; // ignore walls in the average

                totalMyVal += TerritoryUtils.myChannelValue(val, myPlayerNumber);
                totalValA += TerritoryUtils.myChannelValue(val, opponentA);
                totalValB += TerritoryUtils.myChannelValue(val, opponentB);
                samples++;
            }
        }

        if (samples == 0) return 1.0 + MAX_PATH_PENALTY;

        double avgMyVal = totalMyVal / (double) samples;
        double avgValA = totalValA / (double) samples;
        double avgValB = totalValB / (double) samples;

        double desirability = TerritoryUtils.cellPaintValue((int) avgMyVal, (int) avgValA, (int) avgValB, weights);
        double maxDesirability = TerritoryUtils.OWN_GAIN_WEIGHT * 255.0
                + Math.max(weights.weightA(), weights.weightB()) * 255.0;

        double penalty = MAX_PATH_PENALTY * (1.0 - (desirability / maxDesirability));
        return 1.0 + Math.max(0, penalty);
    }

    @Override
    public double getPathDistance(Point start, Point end) {
        List<Point> path = findPath(start, end);
        if (path == null) return Double.MAX_VALUE;

        double dist = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            dist += path.get(i).distance(path.get(i + 1));
        }
        return dist;
    }

    @Override
    public Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos) {
        setBotRadius(self.paintRadius());
        List<Point> path = findPath(currentPos, targetPos);
        if (path == null || path.size() < 2) return Vector2.ZERO;

        // Lookahead to smooth out the path and avoid micro-jitter drifting into walls
        int lookaheadIndex = Math.min(5, path.size() - 1);
        Point next = path.get(lookaheadIndex);
        return Vector2.towards(currentPos, next).normalized();
    }
}
