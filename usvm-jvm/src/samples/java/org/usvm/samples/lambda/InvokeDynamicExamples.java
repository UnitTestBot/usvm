package org.usvm.samples.lambda;

import java.util.function.Function;

public class InvokeDynamicExamples {
    private static class IntWrapper {
        final int value;

        private IntWrapper(int value) {
            this.value = value;
        }

        IntWrapper add(int other) {
            return new IntWrapper(value + other);
        }
    }

    private static int runUnaryFunction(int data, Function<IntWrapper, IntWrapper> f) {
        int result = f.apply(new IntWrapper(data)).value;
        return result + 17;
    }

    private static int runSamFunction(int data, SamBase f) {
        int result = f.samFunction(new IntWrapper(data)).value;
        return result + 17;
    }

    private static int runDefaultFunction(int data, SamBase f) {
        int result = f.defaultFunction(new IntWrapper(data));
        return result + 17;
    }

    public interface SamBase {
        IntWrapper samFunction(IntWrapper data);

        default int defaultFunction(IntWrapper data) {
            if (data == null) {
                return -2;
            }
            return samFunction(data).value + 31;
        }
    }

    private static IntWrapper add(IntWrapper a, int b) {
        return a.add(b);
    }

    private static IntWrapper addTwo(IntWrapper x) {
        return x.add(2);
    }

    public static int testUnaryFunction(int in) {
        return runUnaryFunction(in, d -> d.add(2));
    }

    public static int testMethodRefUnaryFunction(int in) {
        return runUnaryFunction(in, InvokeDynamicExamples::addTwo);
    }

    public static int testCurryingFunction(int in) {
        Function<IntWrapper, IntWrapper> add42 = x -> add(x, 42);
        return runUnaryFunction(in, d -> add42.apply(d.add(2)));
    }

    public static int testSamFunction(int in) {
        return runSamFunction(in, d -> d.add(2));
    }

    public static int testSamWithDefaultFunction(int in) {
        return runDefaultFunction(in, d -> d.add(2));
    }

    private static String runComplexStringConcat(String str, int v) {
        return str + v + 'x' + str + 17 + str;
    }

    public static int testComplexInvokeDynamic(Object unused) {
        String concatenated = runComplexStringConcat("abc", 42);
        return concatenated != null ? 0 : -1;
    }
}
