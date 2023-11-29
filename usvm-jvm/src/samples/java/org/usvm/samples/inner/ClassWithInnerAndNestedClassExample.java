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

        int createInnerClassOutside() {
            ClassWithInnerAndNestedClassExample c = new ClassWithInnerAndNestedClassExample();
            InnerClassExample inner = c.new InnerClassExample();

            return inner.accessOuterClassField();
        }

        int useInnerClassAsParameter(InnerClassExample e) {
            return e.accessOuterClassField();
        }

        int useInheritorAndInnerClass() {
            Inheritor inheritor = new Inheritor();
            return (inheritor.new InnerClassExample()).accessOuterClassField();
        }
    }
}

class Inheritor extends ClassWithInnerAndNestedClassExample {
}
