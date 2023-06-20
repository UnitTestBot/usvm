package org.usvm.samples.operators;

@SuppressWarnings({"PointlessBooleanExpression", "RedundantIfStatement"})
public class Logic {
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
