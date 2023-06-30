package org.usvm.samples.invokes;

// interface with a default implementation of foo
public interface DefaultInterface {
    default int foo() {
        throw new UnsupportedOperationException();
    }
}
