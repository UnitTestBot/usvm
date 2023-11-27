package org.usvm.samples.inner;

public class ClassWithInnerAndNestedClassExample {
    int x;

    class InnerClassExample {
        int y;

        InnerClassExample() {
            y = x + 42;
        }

        int accessOuterClassField() {
            return x;
        }
    }

    static class NestedClassExample {
        int accessOuterClassFieldWithParameter(ClassWithInnerAndNestedClassExample c) {
            return c.x;
        }

        static int staticAccessOuterClassFieldWithParameter(ClassWithInnerAndNestedClassExample c) {
            return c.x;
        }
    }
}
