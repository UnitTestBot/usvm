package org.usvm.samples.arrays;

import org.usvm.samples.objects.WrappedInt;

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

    static int symbolicIndex(int a, WrappedInt b, int index) {
        WrappedInt[] arr = new WrappedInt[]{new WrappedInt(1), new WrappedInt(2), b};
        arr[0].setValue(a);
        if (arr[index].getValue() == 5) {
            if (index == 0) {
                return -1;
            }
            return 0;
        }
        return 1;
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
