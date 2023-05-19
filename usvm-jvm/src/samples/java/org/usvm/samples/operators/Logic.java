package org.usvm.samples.operators;

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
}
