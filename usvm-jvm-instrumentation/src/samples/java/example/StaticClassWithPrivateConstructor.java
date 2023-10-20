package example;

public class StaticClassWithPrivateConstructor {

    int a;
    int b;
    private StaticClassWithPrivateConstructor(int a, int b){}

    static StaticClassWithPrivateConstructor createInstance(int a, int b) {
        return new StaticClassWithPrivateConstructor(a, b);
    }
    static StaticClassWithPrivateConstructor instance = new StaticClassWithPrivateConstructor(0, 0);
}
