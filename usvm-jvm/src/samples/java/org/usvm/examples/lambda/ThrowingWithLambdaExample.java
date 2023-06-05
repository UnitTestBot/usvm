package org.usvm.examples.lambda;

import static org.usvm.api.mock.UMockKt.assume;

public class ThrowingWithLambdaExample {
    // This example mostly checks that we can construct non-static lambda even if it's init section was not analyzed
    // (e.g., an exception was thrown before it).
    boolean anyExample(int[] values, IntPredicate predicate) {
        assume(predicate != null);

        for (int value : values) {
            if (predicate.test(value)) {
                return true;
            }
        }

        return false;
    }

    // To make this lambda non-static, we need to make it use `this` instance.
    @SuppressWarnings({"unused", "ConstantConditions"})
    IntPredicate nonStaticIntPredicate = x -> this != null && x == 42;

    interface IntPredicate {
        boolean test(int value);
    }
}
