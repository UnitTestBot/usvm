package org.usvm.samples.operators;

@SuppressWarnings("ConstantValue")
public class Overflow {
    int shortOverflow(short x, int y) {
        if (y > 10 || y <= 0) {
            return 0;
        }
        if (x + y < 0 && x > 0) {
            throw new IllegalStateException();
        }
        return x + y;
    }
}
