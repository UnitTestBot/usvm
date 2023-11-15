package org.usvm.samples.types;

public class GenericWithUpperBound<C extends Comparable<C>> {
    @SuppressWarnings({"DataFlowIssue"})
    public int excludeComparable(C value) {
        if (!(value instanceof Comparable<?>)) {
            return 0;
        }

        return 1;
    }
}
