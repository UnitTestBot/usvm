package example.fuzz;

import java.util.List;
import java.util.Map;

public class Implementer<T extends Number, S extends Number, E extends String> implements Interface<T, Map<S, E>> {

    T field1;
    S field2;
    Implementer(T f) {
        this.field1 = f;
    }

    @Override
    public void print(T a, Map<S, E> b) {

    }
}
