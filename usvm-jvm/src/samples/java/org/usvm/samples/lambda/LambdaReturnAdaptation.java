package org.usvm.samples.lambda;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import java.util.function.ToIntFunction;

public class LambdaReturnAdaptation {
    public static boolean boxedBoolean(int value) {
        Function<Integer, Boolean> positive = LambdaReturnAdaptation::positive;
        return positive.apply(value);
    }

    public static int boxedInteger(int value) {
        IntFunction<Integer> identity = LambdaReturnAdaptation::identity;
        return identity.apply(value);
    }

    public static int unboxedInteger(Integer value) {
        ToIntFunction<Integer> identity = LambdaReturnAdaptation::boxedIdentity;
        return identity.applyAsInt(value);
    }

    public static long widenedInteger(int value) {
        IntToLongFunction identity = LambdaReturnAdaptation::identity;
        return identity.applyAsLong(value);
    }

    private static boolean positive(int value) {
        return value > 0;
    }

    private static int identity(int value) {
        return value;
    }

    private static Integer boxedIdentity(Integer value) {
        return value;
    }
}
