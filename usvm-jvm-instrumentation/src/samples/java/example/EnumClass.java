package example;

import java.util.ArrayList;

public enum EnumClass {
    A, B, C;

    public int a;
    public int b;
    public static int c;
    public ArrayList<Integer> arr = new ArrayList<>();

    public int lol() {
        b++;
        return a + b + c + arr.size();
    }

}
