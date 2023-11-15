package org.usvm.samples.math;

import static org.usvm.api.mock.UMockKt.assume;

public class DoubleFunctions {
    public double hypo(double a, double b) {
        assume(1.0 < a && a < 10.0);
        assume(1.0 < b && b < 10.0);

        return Math.sqrt(Math.pow(a, 2) + Math.pow(b, 2));
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    public double max(double a, double b) {
        return a > b ? a : b;
    }

    public double circleSquare(double r) {
        // TODO || in if conditions still are not processed correctly https://github.com/UnitTestBot/usvm/issues/95
        if (r < 0) {
            throw new IllegalArgumentException();
        } else if (Double.isNaN(r)) {
            throw new IllegalArgumentException();
        } else if (r > 10000) {
            throw new IllegalArgumentException();
        }
        double square = Math.PI * r * r;
        if (square > 777.85) {
            return square;
        } else {
            return 0;
        }
    }

    public int numberOfRootsInSquareFunction(double a, double b, double c) {
        assume(!(Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c)));

        double result = b * b - 4 * a * c;
        if (result > 0) {
            return 2;
        } else if (result == 0) {
            return 1;
        }
        return 0;
    }
}
