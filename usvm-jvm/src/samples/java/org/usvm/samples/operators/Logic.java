package org.usvm.samples.operators;

@SuppressWarnings("PointlessBooleanExpression")
public class Logic {
    boolean and(boolean x, boolean y) {
        return x && y;
    }

    boolean or(boolean x, boolean y) {
        return x || y;
    }

    boolean xor(boolean x, boolean y) {
        return x ^ y;
    }

    boolean not(boolean x) {
        return !x;
    }

    boolean complex(boolean w, boolean x, boolean y, boolean z) {
        return (x || y) && z ^ w;
    }

    boolean complexWithLocals(int x, short y, long z) {
        boolean equalsXY = x == y;
        boolean equalsYZ = y == z;
        boolean equalsZX = z == x;

        if (((x | y | z) != 1337) ^ true) {
            return false;
        }

        if ((equalsXY || equalsYZ || equalsZX) && !(equalsXY ^ equalsYZ ^ equalsZX)) {
            return false;
        }
        return true;
    }
}
