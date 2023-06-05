package org.usvm.examples.mock.aliasing;

import org.usvm.examples.mock.aliasing.parent.InterfaceFromAnotherPackage;

public class ClassFromTheSamePackage implements InterfaceFromAnotherPackage  {
    @Override
    public int foo(int x) {
        return x;
    }
}
