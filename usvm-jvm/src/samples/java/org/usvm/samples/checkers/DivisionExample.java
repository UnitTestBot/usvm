package org.usvm.samples.checkers;

// NOTE: THIS FILE MUST NOT BE MERGED!

@SuppressWarnings("StatementWithEmptyBody")
public class DivisionExample {
    void divideBy42Constant(int x) {
        int divider = 42;
        int result = x / divider;
        System.out.println(result);
    }

    void divideBySymbolic42(int divider) {
        int result = 100 / divider;
        System.out.println(result);
    }

    void divideBySymbolic42TN(int divider) {
        if (divider == 42) {
            // Do nothing
        } else {
            int result = 100 / divider;
            System.out.println(result);
        }
    }

    void divideBySymbolic42TP(int divider) {
        if (divider != 42) {
            // Do nothing
        } else {
            int result = 100 / divider;
            System.out.println(result);
        }
    }
}
