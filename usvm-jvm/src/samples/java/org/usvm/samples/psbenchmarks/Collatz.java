package org.usvm.samples.psbenchmarks;

/*
    Idea with Collatz conjecture is taken from
    'On Benchmarking the Capability of Symbolic Execution Tools with Logic Bombs'
    by Hui Xu et al.
 */
public class Collatz {

    private static int collatzStep(int a) {
        if (a % 2 == 0) {
            return a / 2;
        }

        return 3 * a + 1;
    }

    public int collatzBomb1(int i) {
        if (i <= 0 || i >= 100) {
            throw new IllegalArgumentException();
        }

        int j = i;
        int loopCount = 0;

        while (j != 1) {
            j = collatzStep(j);
            ++loopCount;
        }

        if (loopCount == 17) {
            return 1;
        }

        return 2;
    }
}
