package org.usvm.samples.codegen;

public class JavaAssert {
    public int assertPositive(int value) {
        assert value > 0;
        return value;
    }
}
