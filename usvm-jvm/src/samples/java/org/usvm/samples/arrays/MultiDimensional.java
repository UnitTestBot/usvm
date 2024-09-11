package org.usvm.samples.arrays;

public class MultiDimensional {
    int sumOf(int a, int b) {
        int[] row1 = new int[]{a, b};
        int[] row2 = new int[]{a, b};
        int[] row3 = new int[]{a, b};
        int[][] sum = new int[3][];
        sum[0] = row1;
        sum[1] = row2;
        sum[2] = row3;
        int x = 0;
        for (int[] row : sum) {
            for (int i : row) {
                x += i;
            }
        }
        return x;
    }

    int sumOfMultiNewArray(int a, int b) {
        int[][] sum = new int[3][2];
        sum[0][0] = a;
        sum[0][1] = b;
        sum[1][0] = a;
        sum[1][1] = b;
        sum[2][0] = a;
        sum[2][1] = b;
        int x = 0;
        for (int[] row : sum) {
            for (int i : row) {
                x += i;
            }
        }
        return x;
    }

    @SuppressWarnings("ConstantValue")
    static int countMulti3NewArray(int x) {
        int result = x;
        int[][][] sum = new int[4][3][2];

        if (sum.length != 4) return -1;

        for (int i = 0; i < 4; i++) {
            int[][] a0 = sum[i];

            if (a0.length != 3) return -1;

            for (int j = 0; j < 3; j++) {
                int[] a1 = a0[j];

                if (a1.length != 2) return -1;

                for (int k = 0; k < 2; k++) {
                    result += a1[k] + 1;
                }
            }
        }

        return result;
    }
}
