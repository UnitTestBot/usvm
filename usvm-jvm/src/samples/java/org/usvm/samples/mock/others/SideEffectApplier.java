package org.usvm.samples.mock.others;

import org.usvm.samples.mock.service.impl.ExampleClass;

public class SideEffectApplier {
    public void applySideEffect(ExampleClass a) {
        a.field += 1;
    }
}
