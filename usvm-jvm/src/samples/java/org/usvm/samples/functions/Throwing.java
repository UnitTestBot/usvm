package org.usvm.samples.functions;

public class Throwing {
    public int throwSometimes(int x) {
        if (x == 1) {
            throw new IllegalArgumentException();
        }
        return x;
    }
}
