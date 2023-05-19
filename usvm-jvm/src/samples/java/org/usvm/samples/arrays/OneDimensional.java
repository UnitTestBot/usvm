package org.usvm.samples.arrays;

public class OneDimensional {
    int sumOf(int[] arr) {
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
}
