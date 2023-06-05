package org.usvm.examples.mock.others;

import org.usvm.examples.mock.service.impl.ExampleClass;

public class SideEffectApplier {
    public void applySideEffect(ExampleClass a) {
        a.field += 1;
    }
}
