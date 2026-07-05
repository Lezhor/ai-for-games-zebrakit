package lezhor.htw.zebrakit.core;

public record Vector2(double x, double y) {
    public static final Vector2 ZERO = new Vector2(0, 0);

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public boolean isZero() {
        return x == 0.0 && y == 0.0;
    }

    public Vector2 normalized() {
        double len = length();
        if (len == 0.0) return ZERO;
        return new Vector2(x / len, y / len);
    }

    public Vector2 scaled(double factor) {
        return new Vector2(x * factor, y * factor);
    }

    public Vector2 plus(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 minus(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public static Vector2 towards(java.awt.Point from, java.awt.Point to) {
        return new Vector2(to.x - from.x, to.y - from.y);
    }
}
