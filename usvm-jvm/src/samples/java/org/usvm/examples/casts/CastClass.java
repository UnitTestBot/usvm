package org.usvm.examples.casts;

import static org.usvm.api.mock.UMockKt.assume;

public class CastClass {
    int x;
    int defaultValue = 5;

    int foo() {
        return defaultValue;
    }

    CastClass castToInheritor() {
        assume(this instanceof CastClassFirstSucc);

        return this;
    }
}
