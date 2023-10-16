package org.usvm.samples.checkers;

@SuppressWarnings({"UnnecessaryLocalVariable"})
public class DivisionExample {
    int divideBy42ConstantSinglePath(int x) {
        int divider = 42;
        int result = x / divider;

        return result;
    }

    int divideBy42ConstantBranching(int x, boolean condition) {
        int divider = 42;

        final int result;
        if (condition) {
            result = x / divider;
        } else {
            result = (x + 1) / divider;
        }

        return result;
    }

    @SuppressWarnings("ConstantValue")
    int divideBy42ConstantWithUnreachableBranch(int x, boolean condition) {
        int divider = 42;

        final int result;
        if (condition && !condition) {
            // Unreachable branch
            result = x / divider;
        } else {
            result = (x + 1) / divider;
        }

        return result;
    }

    int divideBySymbolic42SinglePath(int divider) {
        int result = 100 / divider;

        return result;
    }

    int divideBySymbolic42TrueNegative(int divider) {
        if (divider == 42) {
            return -42;
        } else {
            int result = 100 / divider;

            return result;
        }
    }

    int divideBySymbolic42Branching(int divider, boolean condition) {
        final int result;
        if (condition) {
            result = (divider + 1) / divider;
        } else {
            result = 100 / divider;
        }

        return result;
    }

    int divideBySymbolic42FalsePositive(int divider) {
        int nonNegativeValue = Math.abs(divider);

        final int result;
        if (nonNegativeValue < 0) {
            // Unreachable branch
            result = 100 / divider;
        } else {
            result = 0;
        }

        return result;
    }
}
