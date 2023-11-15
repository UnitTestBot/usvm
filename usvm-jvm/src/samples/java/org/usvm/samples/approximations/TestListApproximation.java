package org.usvm.samples.approximations;

import org.jacodb.approximation.annotation.Approximate;
import org.usvm.api.Engine;
import org.usvm.api.SymbolicList;

@Approximate(TestList.class)
public class TestListApproximation<T> {
    private final SymbolicList<T> storage = Engine.makeSymbolicList();

    public int size() {
        Engine.assume(storage != null);

        return storage.size();
    }

    public void add(T value) {
        Engine.assume(storage != null);

        int size = size();
        storage.insert(size, value);
    }

    public T get(int idx) {
        Engine.assume(storage != null);

        return storage.get(idx);
    }
}
