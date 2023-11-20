package org.usvm.samples.approximations;

import org.jacodb.approximation.annotation.Approximate;
import org.usvm.api.Engine;
import org.usvm.api.SymbolicMap;

@Approximate(TestMap.class)
public class TestMapApproximation<K, V> {
    private final SymbolicMap<K, V> storage = Engine.makeSymbolicMap();

    int size() {
        Engine.assume(storage != null);
        return storage.size();
    }

    boolean containsKey(final K key) {
        Engine.assume(storage != null);
        return storage.containsKey(key);
    }

    V put(final K key, final V value) {
        Engine.assume(storage != null);
        V oldValue = null;
        if (storage.containsKey(key)) {
            oldValue = storage.get(key);
        }
        storage.set(key, value);
        return oldValue;
    }

    V get(final K key) {
        Engine.assume(storage != null);
        return storage.get(key);
    }

    void putAll(final TestMapApproximation<K, V> other) {
        Engine.assume(storage != null);
        Engine.assume(other.storage != null);

        storage.merge(other.storage);
    }
}
