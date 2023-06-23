package example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class A {


    public A() {
    }

    public A(int field) {
        this.field = field;
    }

    int field = 666;

    static int staticField2 = 1;
    static int staticField1;
    final static ArrayList<Integer> a = new ArrayList<>();
    final static int staticField3;

    static {
        a.add(B.l.get(0));
        a.add(12);
        a.add(555);
        staticField3 = a.size();
        staticField1 = 777;
        if (staticField1 == 1) {
            staticField2 = 5;
        } else {
            staticField2 = 6;
        }
    }

    static Integer lolInteger = 2;

    public int methodWithEnum(EnumClass e) {
        e.a = 7778;
        EnumClass.c++;
        return e.lol();
    }

    public int isA(int b) {
        staticField1++;
//        lolInteger = 239;
//        int c = lol;
//        int newA = isA1(a, c);
//        return a == 1 ? newA : -1;
        return staticField1;
    }

    private int isA1(int a, int b) {
        if (a == b) {
            return a;
        }
        return a + 1;
    }

    private int methodWithBug() {
        int[] array = new int[3];
        return array[4];
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

    public static List<Integer> staticJavaStdLibCall() {
        List<Integer> l = Arrays.asList(543, 432, 1, -23);
        l.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer integer, Integer t1) {
                return integer.compareTo(t1);
            }
        });
        return l;
    }

}