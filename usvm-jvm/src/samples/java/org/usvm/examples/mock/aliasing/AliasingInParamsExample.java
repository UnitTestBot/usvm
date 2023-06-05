package org.usvm.examples.mock.aliasing;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.examples.mock.aliasing.parent.InterfaceFromAnotherPackage;

public class AliasingInParamsExample {
    int example(InterfaceFromAnotherPackage fst, ClassFromTheSamePackage snd, int x) {
        assume(fst != null && snd != null);
        if (fst == snd) {
            return fst.foo(x); // unreachable with package based mock approach
        } else {
            return snd.foo(x);
        }
    }
}
