package org.usvm.samples.approximations;

import java.util.HashMap;
import java.util.Map;

public class TestMap<K, V> {
    private final Map<K, V> data = new HashMap<>();

    int size() {
        return data.size();
    }

    boolean containsKey(final K key) {
        return data.containsKey(key);
    }

    V put(final K key, final V value) {
        return data.put(key, value);
    }

    V get(final K key) {
        return data.get(key);
    }

    void putAll(final TestMap<K, V> other) {
        data.putAll(other.data);
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
