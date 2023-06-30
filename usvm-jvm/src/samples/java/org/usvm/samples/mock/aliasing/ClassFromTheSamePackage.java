package org.usvm.samples.mock.aliasing;

import org.usvm.samples.mock.aliasing.parent.InterfaceFromAnotherPackage;

public class ClassFromTheSamePackage implements InterfaceFromAnotherPackage {
    @Override
    public int foo(int x) {
        return x;
    }
}
