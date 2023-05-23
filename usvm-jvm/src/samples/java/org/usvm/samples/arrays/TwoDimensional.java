package org.usvm.samples.arrays;

public class TwoDimensional {
    int sumOf() {
        int [][]sum = new int[][] {{1, 2,}, {2, 3,}, {3, 4}};
        int x = 0;
        for (int[] row : sum) {
            for (int i : row) {
                x += i;
            }
        }
        return x;
    }
}
