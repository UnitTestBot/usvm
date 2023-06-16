package org.usvm.samples.symbolic;

import static org.usvm.api.mock.UMockKt.assume;

public class ClassWithComplicatedMethods {
    int a = 15;

    // Do not substitute this one, result should be positive
    public ClassWithComplicatedMethods(int a, int b) {
        assume(a > 10);
        assume(b > 10);

        this.a = a + b;
    }

    // This one should be substituted, the result should be negative because of the substitution
    public ClassWithComplicatedMethods(double a, double b) {
        assume(a > 0);
        assume(b > 0);

        this.a = (int) (a + b);
    }

    public ClassWithComplicatedMethods createWithOriginalConstructor(int a, int b) {
        return new ClassWithComplicatedMethods(a, b);
    }

    ClassWithComplicatedMethods createWithSubstitutedConstructor(double a, double b) {
        return new ClassWithComplicatedMethods(a, b);
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    public ClassWithComplicatedMethods applyMethodWithSideEffectAndReturn(int x) {
        methodWithSideEffect(x);

        if (a == 2821) {
            return this;
        } else {
            this.a = 10;
            return this;
        }
    }

    // return `this` with a > 100 and at the beginning x != this.a
    public ClassWithComplicatedMethods returnSubstitutedMethod(int x) {
        return constructComplicatedMethod(x);
    }

    public double sqrt2() {
        return Math.sqrt(2);
    }

    private ClassWithComplicatedMethods constructComplicatedMethod(int x) {
        this.a = x + 100;
        return this;
    }

    private void methodWithSideEffect(int x) {
        assume(a == 15);
        assume(x > 0);

        if (Math.sqrt(x) == x) {
            a = 2821;
        } else {
            a = 2822;
        }
    }

    // UMockKt.assume must not be mocked by the engine
    @SuppressWarnings("ConstantConditions")
    public int assumesWithMocks(int x) {
        assume(x > 5);
        assume(x < 8);

        if (x > 5 && x < 8) {
            return 1;
        } else {
            return 2;
        }
    }
}
