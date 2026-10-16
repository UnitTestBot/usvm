package org.usvm.samples.lambda;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class LambdaArgumentAdaptation {
    public static int isWhitespace(int codePoint) {
        if (codePoint != ' ' && codePoint != 'A') {
            return -1;
        }

        Predicate<Integer> isDelimiter = Character::isWhitespace;
        return isDelimiter.test(codePoint) ? 1 : 0;
    }

    public static boolean boxedPositive(Integer value) {
        Predicate<Integer> positive = LambdaArgumentAdaptation::positiveInt;
        return positive.test(value);
    }

    public static boolean boxedWideningPositive(Integer value) {
        Predicate<Integer> positive = LambdaArgumentAdaptation::positiveLong;
        return positive.test(value);
    }

    public static boolean primitivePositive(int value) {
        IntPredicate positive = LambdaArgumentAdaptation::positiveInt;
        return positive.test(value);
    }

    private static boolean positiveInt(int value) {
        return value > 0;
    }

    private static boolean positiveLong(long value) {
        return value > 0;
    }
}
