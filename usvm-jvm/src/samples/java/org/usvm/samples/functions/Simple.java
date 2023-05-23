package org.usvm.samples.functions;

public class Simple {
    public int calcTwoFunctions(int x, int y) {
        return function1(x) + function2(y);
    }

    private int function1(int x) {
        return x * x;
    }

    private int function2(int y) {
        return y;
    }
}
