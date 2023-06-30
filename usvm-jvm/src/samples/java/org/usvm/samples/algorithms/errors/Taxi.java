package org.usvm.samples.algorithms.errors;

import java.util.Arrays;

// http://codeforces.com/contest/158/problem/B, Verdict: RE12 (because of a negative index in the array at some point)
public class Taxi {
    public int runTest(int length, int[] groups) {
        if (length <= 0 || length > 100000) {
            throw new IllegalArgumentException("Length not in 1..10^5");
        }

        if (groups.length != length) {
            throw new IllegalArgumentException("Array size doesn't match with given size");
        }

        for (int i = 0; i < length; i++) {
            if (groups[i] < 1 || groups[i] > 4) {
                throw new IllegalArgumentException("All numbers in \"groups\" must be in 1..4");
            }
        }

        return findAnswer(length, groups);
    }

    private int[] reverse(int[] array) {
        int[] reversed = new int[array.length];
        for (int i = array.length - 1; i >= 0; i--) {
            reversed[i] = array[array.length - i - 1];
        }

        return reversed;
    }

    private int findAnswer(int length, int[] groups) {
        Arrays.sort(groups);
        int[] reversed = reverse(groups);

        int j = length - 1;
        int answer = 0;
        for (int i = 0; i < length; i++) {
            if (i < j) {
                if (reversed[i] + reversed[j] <= 4) {
                    int drive = reversed[j];
                    while (reversed[i] + drive <= 4) {
                        j--;
                        drive += reversed[j];
                    }
                }
                answer++;
            } else if (i == j) {
                answer++;
            } else {
                break;
            }
        }
        return answer;
    }
}
