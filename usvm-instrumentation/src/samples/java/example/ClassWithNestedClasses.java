package example;

public class ClassWithNestedClasses {

    static class A {
        int a = 1;

        static class B {

            public B(int b) {
                this.b = b;
            }
            int b = 2;

            public B getB() {
                b++;
                return new B(b);
            }
        }
    }
}
