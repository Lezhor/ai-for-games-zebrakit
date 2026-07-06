package lezhor.htw.zebrakit.strategy.bresenham;

import lezhor.htw.zebrakit.core.BoardConstants;
import lezhor.htw.zebrakit.core.GameState;

/**
 * The board's walls as a downscaled boolean grid, <em>thickened</em> (dilated) by
 * a configurable radius on init. This is the special "wall evaluator": it is not
 * in the evaluator list — instead {@link RayScanner} queries {@link #isBlocked}
 * per pixel to stop a ray early when it reaches a wall, and the thickening plus
 * the scanner's min-wall-distance rule are what keep bots from driving into walls
 * without any steering behaviour. Walls are static, so this is built once.
 */
public final class WallField {
    private final int cellPixels;
    private final int gridSize;
    private final double radiusPx;

    private boolean[][] blocked; // true = wall or within radiusPx of one

    public WallField(int gridScale, double radiusPx) {
        this.cellPixels = gridScale;
        this.gridSize = BoardConstants.MAP_SIZE / gridScale;
        this.radiusPx = radiusPx;
    }

    /** Precompute the thickened wall grid. Idempotent — safe to call every tick; only the first does work. */
    public void init(GameState state) {
        if (blocked != null) return;

        boolean[][] wall = new boolean[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                wall[gx][gy] = !state.isWalkable(cellCenter(gx), cellCenter(gy));
            }
        }

        int radiusCells = (int) Math.ceil(radiusPx / cellPixels);
        boolean[][] dilated = new boolean[gridSize][gridSize];
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gy = 0; gy < gridSize; gy++) {
                if (wall[gx][gy]) {
                    markDisk(dilated, gx, gy, radiusCells);
                }
            }
        }
        blocked = dilated;
    }

    /** True if the pixel is a (thickened) wall or out of bounds. */
    public boolean isBlocked(int x, int y) {
        if (x < 0 || y < 0 || x >= BoardConstants.MAP_SIZE || y >= BoardConstants.MAP_SIZE) return true;
        return blocked[clamp(x / cellPixels)][clamp(y / cellPixels)];
    }

    private void markDisk(boolean[][] grid, int cx, int cy, int radiusCells) {
        int r2 = radiusCells * radiusCells;
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dy = -radiusCells; dy <= radiusCells; dy++) {
                if (dx * dx + dy * dy > r2) continue;
                int gx = cx + dx;
                int gy = cy + dy;
                if (gx >= 0 && gy >= 0 && gx < gridSize && gy < gridSize) grid[gx][gy] = true;
            }
        }
    }

    private int cellCenter(int gridIndex) {
        return gridIndex * cellPixels + cellPixels / 2;
    }

    private int clamp(int i) {
        if (i < 0) return 0;
        if (i >= gridSize) return gridSize - 1;
        return i;
    }
}
