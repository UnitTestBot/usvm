package example.fuzz;

import example.A;
import example.EnumClass;

public class Simple {

    public int fuzz(A a1, EnumClass e) {
        if (a1.field == a1.methodWithEnum(e)) {
            return 1;
        } else {
            return -1;
        }
    }
}
