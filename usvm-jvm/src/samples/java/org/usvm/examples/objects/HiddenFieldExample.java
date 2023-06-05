package org.usvm.examples.objects;

import static org.usvm.api.mock.UMockKt.assume;

public class HiddenFieldExample {
    public int checkHiddenField(HiddenFieldSuperClass o) {
        assume(!(o instanceof HiddenFieldSuccClass));

        if (o.a == 1 && o.b == 2) {
            return 1;
        }
        return 2;
    }

    public int checkSuccField(HiddenFieldSuccClass o) {
        if (o.a == 1) {
            return 1;
        }
        if (o.b == 2) {
            return 2;
        }
        if (((HiddenFieldSuperClass) o).b == 3) {
            return 3;
        }
        return 4;
    }
}