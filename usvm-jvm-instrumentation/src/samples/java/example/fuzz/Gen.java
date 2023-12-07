package example.fuzz;

public class Gen<S extends Comparable<S>> {

    boolean compareGen(S arg1, S arg2) {
        return arg1.equals(arg2);
    }
}
