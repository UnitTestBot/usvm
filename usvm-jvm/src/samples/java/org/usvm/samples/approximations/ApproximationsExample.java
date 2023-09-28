package org.usvm.samples.approximations;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public class ApproximationsExample {
    public int modifyList(int execution) {
        List<Object> list = new ArrayList<>();
        if (execution == 0) {
            // IOB
            if (list.get(5) == null) {
                return -1;
            }
        }

        Object stub = new Object();
        for (int i = 0; i < 3; i++) {
            list.add(new Object());
        }

        if (execution == 1) {
            if (list.size() == 3) {
                return 1;
            } else {
                return -1;
            }
        }

        list.add(0, stub);
        list.add(stub);

        if (execution == 2) {
            if (list.size() == 5) {
                return 2;
            } else {
                return -1;
            }
        }

        if (execution == 3) {
            if (list.get(0) == list.get(list.size() - 1)) {
                return 3;
            } else {
                return -1;
            }
        }

        if (execution == 4) {
            if (list.get(0) != list.get(1)) {
                return 4;
            } else {
                return -1;
            }
        }

        return 0;
    }

    public int testOptionalDouble(int execution) {
        double value = 3.0;
        OptionalDouble od = OptionalDouble.of(value);

        if (execution == 0) {
            if (od.isPresent()) {
                return 0;
            } else {
                return -1;
            }
        }

        if (execution == 1) {
            if (od.getAsDouble() == value) {
                return 1;
            } else {
                return -1;
            }
        }

        OptionalDouble e = OptionalDouble.empty();

        if (execution == 2) {
            if (!e.isPresent()) {
                return 2;
            } else {
                return -1;
            }
        }

        return 0;
    }
}
