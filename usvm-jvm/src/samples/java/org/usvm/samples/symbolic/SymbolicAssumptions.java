package org.usvm.samples.symbolic;

import org.usvm.api.Engine;

public class SymbolicAssumptions {
    public int rejectNegativeSymbolicInput(Object instance, int value) {
        if (instance == null) {
            return 0;
        }

        if (value < 0) {
            Engine.assumeSymbolic(instance, false);
            return -1;
        }

        return 1;
    }

    public int ignoreConcreteInput() {
        Engine.assumeSymbolic(new Object(), false);
        return 1;
    }

    public int ignoreNullInput() {
        Engine.assumeSymbolic(null, false);
        return 1;
    }

    public int selectSymbolicOrConcreteInput(Object instance, int index) {
        if (instance == null || index < 0 || index > 1) {
            return 0;
        }

        Object[] choices = {instance, new Object()};
        Engine.assumeSymbolic(choices[index], false);
        return index + 1;
    }
}
