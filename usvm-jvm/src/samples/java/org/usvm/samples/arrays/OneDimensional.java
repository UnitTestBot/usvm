package org.usvm.samples.arrays;

public class OneDimensional {
    static int sumOf(int[] arr) {
        int sum = 0;
        boolean allNonNegative = true;
        for (int x : arr) {
            sum += x;
            allNonNegative &= x >= 0;
        }

        if (allNonNegative && sum < 0) {
            throw new IllegalArgumentException();
        }

        return sum;
    }

    static int[] minus(int[] a, int[] b) {
        if (a != null && a.length == 0) {
            return null;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] -= b[i];
        }
        return a;
    }
}
