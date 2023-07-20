package example;

public class C {

    static int a(Class<?> clazz) {
        return 1;
    }

    public static int lol() {
        return a(IllegalAccessException.class);
    }

}
