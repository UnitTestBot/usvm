package org.usvm.samples.symbolic;// TODO unsupported constructions
//package org.usvm.examples.symbolic;
//
//import org.usvm.api.annotation.UtClassMock;
//import org.usvm.api.annotation.UtConstructorMock;
//import org.usvm.api.mock.UMockKt;
//
//import static org.usvm.api.mock.UMockKt.assume;
//
//@UtClassMock(target = ClassWithComplicatedMethods.class)
//public class ComplicatedMethodsSubstitutionsStorage {
//    int a;
//
//    @UtConstructorMock
//    public ComplicatedMethodsSubstitutionsStorage(double a, double b) {
//        assume(a < 0);
//        assume(b < 0);
//
//        this.a = (int) (a + b);
//    }
//
//    public ComplicatedMethodsSubstitutionsStorage constructComplicatedMethod(int x) {
//        assume(x > 100);
//        assume(x != a);
//
//        this.a = x;
//        return this;
//    }
//
//    public void methodWithSideEffect(int x) {
//        assume(a == 15);
//        assume(x > 0);
//
//        double result = Math.sqrt(x);
//
//        if (result == x) {
//            a = 2821;
//        } else {
//            a = 2822;
//        }
//    }
//}