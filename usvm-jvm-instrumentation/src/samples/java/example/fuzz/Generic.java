package example.fuzz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Generic<T extends Number & Comparable<T>> extends Gen<T> {

    T field;

    Generic(Map<Integer, ArrayList<? extends List<T>>> field) {
        this.field = field.get(0).get(0).get(0);
    }

    public <S extends Number> int example2(Iterable<S> iter) {
        return 1;
    }

    public <S extends Number> int example(ArrayList<ArrayList<? extends Number>> arr, S arg2, T arg3) {
        if (arr.size() != 3) return -1;
        if (arr.get(0).get(0) instanceof Integer && (Integer) arr.get(0).get(0) == 1) {
            if ((Integer) arr.get(0).get(1) == 2) {
                if ((Integer) arr.get(0).get(2) == 3) {
                    if (arr.get(0).get(0).equals(arg2)) {
                        return 1;
                    }
                    else {
                        return -1;
                    }
                }
            }
        }
        return 0;
    }

}
