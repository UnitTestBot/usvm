package org.usvm.examples.models;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.examples.objects.WrappedInt;

public class CompositeModelMinimizationExample {
    public boolean singleNotNullArgumentInitializationRequired(WrappedInt a) {
        assume(a != null);
        return a.getValue() == 1;
    }

    public boolean sameArgumentsInitializationRequired(WrappedInt a, WrappedInt b) {
        assume(a == b);
        return b.getValue() == 1;
    }

    public boolean distinctNotNullArgumentsSecondInitializationNotExpected(WrappedInt a, WrappedInt b) {
        assume(a != null && b != null && a != b);
        return a.getValue() == 1;
    }

    public boolean distinctNotNullArgumentsInitializationRequired(WrappedInt a, WrappedInt b) {
        assume(a != null && b != null && a != b);
        return a.getValue() + 1 != b.getValue();
    }
}
