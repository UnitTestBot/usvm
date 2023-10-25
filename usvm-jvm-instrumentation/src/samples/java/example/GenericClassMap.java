package example;

import java.util.Map;

public class GenericClassMap<U extends Map<? extends Number, String>> {
    GenericClassMap(U a){
        this.a = a;
    }
    U a;

    public Object get(Number n) {
        return a.get(n);
    }
}
