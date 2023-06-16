package org.usvm.samples.functions;

public class Simple {
    public int calcTwoFunctions(int x, int y) {
        int res = square(x) + id(y);
        if (res < 0 && y >= 0) {
            return 0;
        }
        return 1;
    }

    private int square(int x) {
        return x * x;
    }

    private int id(int y) {
        return y;
    }

    public int factorial(int n) {
        if (n > 10 || n < 0) {
            return 1;
        }
        return (n == 0 ? 1 : factorial(n - 1));
    }
}
