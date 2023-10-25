package example;

import java.util.ArrayList;
import java.util.List;

public class ClassWithStaticFields {

    public static int a = 0;
    public final static int b = 0;
    public static final List<Integer> c = new ArrayList<>();
    public static final Integer d = 0;
    public static final Integer e;

    private static int f;

    public int g;
    public final int h = 23;

    public final int i;

    static {
        e = 12;
    }

    public ClassWithStaticFields(int i) {
        this.i = i;
    }

}
