package org.usvm.samples.mock;

import org.usvm.samples.mock.others.ClassWithStaticField;
import org.usvm.samples.mock.others.Generator;

public class MockStaticFieldExample {
    @SuppressWarnings("unused")
    private static Generator privateGenerator;
    public static Generator publicGenerator;

    public static final ClassWithStaticField staticFinalField = new ClassWithStaticField();
    public static ClassWithStaticField staticField = new ClassWithStaticField();

    public int calculate(int threshold) {
        int a = privateGenerator.generateInt();
        int b = publicGenerator.generateInt();
        if (threshold < a + b) {
            return threshold;
        }
        return a + b + 1;
    }

    public int checkMocksInLeftAndRightAssignPartFinalField() {
        staticFinalField.intField = 5;
        staticFinalField.anotherIntField = staticFinalField.foo();

        return staticFinalField.anotherIntField;
    }

    public int checkMocksInLeftAndRightAssignPart() {
        staticField.intField = 5;
        staticField.anotherIntField = staticField.foo();

        return staticField.anotherIntField;
    }
}