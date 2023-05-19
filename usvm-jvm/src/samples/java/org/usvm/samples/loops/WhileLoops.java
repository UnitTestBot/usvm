package org.usvm.samples.loops;

public class WhileLoops {
    int singleLoop(int n) {
        int i = 0;
        while (i < n) {
            i++;
            if (i == 5) {
                return 0;
            }
        }
        if (0 == i) {
            return 1;
        }
        return 2;
    }

    int smallestPowerOfTwo(int n) {
        int st = 1;
        while (st < n) {
            st *= 2;
        }
        if (st == n) {
            return 0;
        }
        if (st == 1) {
            return 1;
        }
        return 2;
    }
}
