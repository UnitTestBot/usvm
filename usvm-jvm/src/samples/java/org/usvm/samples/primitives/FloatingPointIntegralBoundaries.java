package org.usvm.samples.primitives;

public class FloatingPointIntegralBoundaries {
    public static boolean doubleAtLongUpperBoundary(double value) {
        if (value != 0x1.0p63) {
            return false;
        }

        return (long) value == Long.MAX_VALUE;
    }

    public static boolean floatAtIntUpperBoundary(float value) {
        if (value != 0x1.0p31f) {
            return false;
        }

        return (int) value == Integer.MAX_VALUE;
    }

    public static boolean floatAtLongUpperBoundary(float value) {
        if (value != 0x1.0p63f) {
            return false;
        }

        return (long) value == Long.MAX_VALUE;
    }
}
