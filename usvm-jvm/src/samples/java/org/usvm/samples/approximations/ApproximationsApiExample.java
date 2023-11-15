package org.usvm.samples.approximations;

public class ApproximationsApiExample {
    public static int symbolicList(TestList<Integer> list) {
        if (list.size() < 10) {
            return 0;
        }

        if (list.get(3) == 5) {
            return 1;
        }

        if (list.get(2) == 7) {
            return 2;
        }

        return 3;
    }
}
