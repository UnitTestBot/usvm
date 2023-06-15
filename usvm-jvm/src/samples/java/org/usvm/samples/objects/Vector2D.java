package org.usvm.samples.objects;

public class Vector2D {
    int x;
    int y;

    boolean isZero() {
        return x == 0 && y == 0;
    }

    long hypotSquared() {
        return (long) x * x + (long) y * y;
    }

    boolean isPerpendicularTo(Vector2D other) {
        long scalar = (long)x * other.x + (long)y * other.y;
        if (scalar == 0) {
            return true;
        } else {
            return false;
        }
    }

    boolean isCollinearTo(Vector2D other) {
        long cross = (long)x * other.y - (long)y * other.x;
        if (cross == 0) {
            return true;
        } else {
            return false;
        }
    }
}
