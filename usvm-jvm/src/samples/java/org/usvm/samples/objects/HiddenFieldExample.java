package org.usvm.samples.objects;

public class HiddenFieldExample {
    public int checkHiddenField(HiddenFieldSuperClass o) {
        if (o instanceof HiddenFieldSuccClass) {
            return 0;
        }

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