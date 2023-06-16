package org.usvm.samples.mixed;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.samples.objects.ObjectWithPrimitivesClass;

public class Simplifier {
    public ObjectWithPrimitivesClass simplifyAdditionWithZero(ObjectWithPrimitivesClass fst) {
        assume(fst != null);

        fst.x = 0;

        fst.x += fst.shortValue;

        return fst;
    }
}
