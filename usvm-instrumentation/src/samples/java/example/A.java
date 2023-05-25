package example;

public class A {

    int field = 777;

    public int isA(int a) {
        int newA = isA1(a, 1);
        field = 239;
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


}