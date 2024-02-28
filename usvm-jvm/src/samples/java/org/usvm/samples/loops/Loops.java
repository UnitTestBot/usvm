package org.usvm.samples.loops;

import org.usvm.api.Engine;

public class Loops {

    public static int loopWithConcreteBound(int n) {
        int result = 0;
        for (int i = 0; i < 10; i++) {
            result += i;
        }
        return result;
    }

    public static int loopWithSymbolicBound(int n) {
        Engine.assume(n <= 10);

        int result = 0;
        for (int i = 0; i < n; i++) {
            result += i;
        }
        return result;
    }

    public static int loopWithConcreteBoundAndSymbolicBranching(boolean condition) {
        int result = 0;
        for (int i = 0; i < 10; i++) {
            if (condition && i % 2 == 0) {
                result += i;
            }
        }
        return result;
    }

    public static int loopWithSymbolicBoundAndSymbolicBranching(int n, boolean condition) {
        Engine.assume(n <= 10);

        int result = 0;
        for (int i = 0; i < n; i++) {
            if (condition && i % 2 == 0) {
                result += i;
            }
        }
        return result;
    }

    public static int loopWithSymbolicBoundAndComplexControlFlow(int n, boolean condition) {
        Engine.assume(n <= 10);

        int result = 0;
        for (int i = 0; i < n; i++) {
            if (condition && i == 3) break;

            if (i % 2 != 0) continue;

            result += i;
        }
        return result;
    }
}
