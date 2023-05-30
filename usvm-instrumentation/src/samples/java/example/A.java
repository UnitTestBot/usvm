package example;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class A {

    int field = 777;
    static int lol = 1;
    static Integer lolInteger = 2;

    public int isA(int a) {
        lol = 123;
        lolInteger = 239;
        int newA = isA1(a, 1);
        return a == 1 ? newA : -1;
    }

    private int isA1(int a, int b) {
        if (a == b) {
            return a;
        }
        return a + 1;
    }

    public int indexOf(int[] arr, int el) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == el) return i;
        }
        return -1;
    }

    public <T> int indexOfT(T[] arr, T el) {
        for (int i = 0; i < arr.length; i++) {
            if (el.equals(arr[i])) return i;
        }
        return -1;
    }

    public List<Integer> javaStdLibCall() {
        List<Integer> l = Arrays.asList(1, 2, 3);
        l.sort(Comparator.naturalOrder());
        return l;
    }


}