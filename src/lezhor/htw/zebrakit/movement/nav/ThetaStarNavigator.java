package lezhor.htw.zebrakit.movement.nav;

import lenz.htw.zebrakit.e;
import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.core.Vector2;

import java.awt.Point;
import java.util.*;

public class ThetaStarNavigator implements Navigator {
    private static final int MAP_SIZE = BoardConstants.MAP_SIZE;
    private final int inflationRadius;
    private final int scale;
    private final int scaledSize;

    private boolean[][] grid; // true if walkable, false if obstacle

    public ThetaStarNavigator() {
        this(8, 2); // Default inflation=8 pixels, scale=2 (down to 512x512)
    }

    public ThetaStarNavigator(int inflationRadius, int scale) {
        this.inflationRadius = inflationRadius;
        this.scale = scale;
        this.scaledSize = MAP_SIZE / scale;
    }

    private interface WalkabilityChecker {
        boolean isWalkable(int x, int y);
    }

    @Override
    public void initialize(long seed) {
        e board = new e(seed);
        initGridAndInflate((x, y) -> board.a(x, y) != 0);
    }

    @Override
    public void initialize(lenz.htw.zebrakit.net.NetworkClient client) {
        initGridAndInflate(client::isWalkable);
    }

    private void initGridAndInflate(WalkabilityChecker checker) {
        grid = new boolean[scaledSize][scaledSize];

        for (int sx = 0; sx < scaledSize; sx++) {
            for (int sy = 0; sy < scaledSize; sy++) {
                grid[sx][sy] = true;
            }
        }

        int scaledInflation = (int) Math.ceil((double) inflationRadius / scale);
        int scaledInfSq = scaledInflation * scaledInflation;

        // Populate grid and apply inflation
        for (int x = 0; x < MAP_SIZE; x++) {
            for (int y = 0; y < MAP_SIZE; y++) {
                if (!checker.isWalkable(x, y)) {
                    int sx = x / scale;
                    int sy = y / scale;

                    int minX = Math.max(0, sx - scaledInflation);
                    int maxX = Math.min(scaledSize - 1, sx + scaledInflation);
                    int minY = Math.max(0, sy - scaledInflation);
                    int maxY = Math.min(scaledSize - 1, sy + scaledInflation);

                    for (int ix = minX; ix <= maxX; ix++) {
                        for (int iy = minY; iy <= maxY; iy++) {
                            if (grid[ix][iy]) {
                                int dx = ix - sx;
                                int dy = iy - sy;
                                if (dx * dx + dy * dy <= scaledInfSq) {
                                    grid[ix][iy] = false;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<Point> findPath(Point start, Point end) {
        if (grid == null) return null;

        Point sStart = new Point(start.x / scale, start.y / scale);
        Point sEnd = new Point(end.x / scale, end.y / scale);

        if (!isValid(sStart.x, sStart.y) || !grid[sStart.x][sStart.y] ||
            !isValid(sEnd.x, sEnd.y) || !grid[sEnd.x][sEnd.y]) {
            return null; // Start or end is in obstacle or out of bounds
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Point, Node> allNodes = new HashMap<>();

        Node startNode = new Node(sStart.x, sStart.y, 0, heuristic(sStart, sEnd), null);
        open.add(startNode);
        allNodes.put(sStart, startNode);

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        double[] cost = {1, 1, 1, 1, 1.414, 1.414, 1.414, 1.414};

        Node targetNode = null;

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

                // Theta* optimization: check line of sight to grandparent
                Node parentNode = current.parent != null ? current.parent : current;
                if (lineOfSight(parentNode.x, parentNode.y, nx, ny)) {
                    double newG = parentNode.g + distance(parentNode.x, parentNode.y, nx, ny);
                    if (newG < neighbor.g) {
                        neighbor.g = newG;
                        neighbor.f = newG + heuristic(nPoint, sEnd);
                        neighbor.parent = parentNode;
                        open.remove(neighbor);
                        open.add(neighbor);
                    }
                } else {
                    double newG = current.g + cost[i];
                    if (newG < neighbor.g) {
                        neighbor.g = newG;
                        neighbor.f = newG + heuristic(nPoint, sEnd);
                        neighbor.parent = current;
                        open.remove(neighbor);
                        open.add(neighbor);
                    }
                }
            }
        }

        if (targetNode == null) return null;

        List<Point> path = new ArrayList<>();
        Node curr = targetNode;
        while (curr != null) {
            // Restore original scale (center of the cell)
            path.add(new Point(curr.x * scale + scale / 2, curr.y * scale + scale / 2));
            curr = curr.parent;
        }
        Collections.reverse(path);

        // Ensure exact start and end match
        path.set(0, start);
        path.set(path.size() - 1, end);

        return path;
    }

    @Override
    public double getPathDistance(Point start, Point end) {
        List<Point> path = findPath(start, end);
        if (path == null) return Double.MAX_VALUE;

        double totalDist = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            totalDist += path.get(i).distance(path.get(i + 1));
        }
        return totalDist;
    }

    @Override
    public Vector2 getNextMoveDirection(GameState state, BotContext self, Point currentPos, Point targetPos) {
        List<Point> path = findPath(currentPos, targetPos);
        if (path == null || path.size() < 2) {
            return Vector2.ZERO;
        }
        // The path[0] is the current position, path[1] is the next waypoint
        Point nextPoint = path.get(1);
        return Vector2.towards(currentPos, nextPoint).normalized();
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < scaledSize && y >= 0 && y < scaledSize;
    }

    private double heuristic(Point a, Point b) {
        return distance(a.x, a.y, b.x, b.y);
    }

    private double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    // Bresenham's line algorithm for line-of-sight
    private boolean lineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int x = x0;
        int y = y0;
        int n = 1 + dx + dy;
        int x_inc = (x1 > x0) ? 1 : -1;
        int y_inc = (y1 > y0) ? 1 : -1;
        int error = dx - dy;
        dx *= 2;
        dy *= 2;

        for (; n > 0; --n) {
            if (!isValid(x, y) || !grid[x][y]) return false;

            if (error > 0) {
                x += x_inc;
                error -= dy;
            } else if (error < 0) {
                y += y_inc;
                error += dx;
            } else {
                // error == 0
                x += x_inc;
                y += y_inc;
                error += dx - dy;
                n--;
            }
        }
        return true;
    }

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
            this.closed = false;
        }
    }
}
