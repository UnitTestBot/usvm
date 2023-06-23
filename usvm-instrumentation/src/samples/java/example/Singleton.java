package example;

import java.util.ArrayList;

public class Singleton {

    private Singleton() {
        arr = new ArrayList<>();
    }
    private final ArrayList<Integer> arr;
    private static Singleton instance;
    public static Singleton getInstance() {
        if (instance == null) instance = new Singleton();
        return instance;
    }

    public void addToArray(Integer a) {
        arr.add(a);
    }
}
