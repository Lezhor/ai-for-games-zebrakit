package lezhor.htw.zebrakit.core;

import lenz.htw.zebrakit.PowerupType;

import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Tracks currently-active powerups on the board from spawn/pickup events. */
public class PowerupTracker {
    private final Map<Point, PowerupType> active = new HashMap<>();

    public void onSpawn(Point at, PowerupType type) {
        active.put(at, type);
    }

    /** Removes whichever tracked powerup is nearest to `near`, within `matchRadius`, if any. */
    public void onPickup(Point near, double matchRadius) {
        Point closest = null;
        double bestDist = Double.MAX_VALUE;
        for (Point p : active.keySet()) {
            double dist = p.distance(near);
            if (dist <= matchRadius && dist < bestDist) {
                bestDist = dist;
                closest = p;
            }
        }
        if (closest != null) {
            active.remove(closest);
        }
    }

    public boolean isPowerupTarget(Point target, double matchRadius) {
        for (Point p : active.keySet()) {
            if (p.distance(target) < matchRadius) {
                return true;
            }
        }
        return false;
    }

    public Map<Point, PowerupType> active() {
        return Collections.unmodifiableMap(active);
    }
}
