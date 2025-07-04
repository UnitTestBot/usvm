package org.usvm.api.internal;

import org.usvm.api.SymbolicMap;

import java.util.HashMap;
import java.util.Map;

public class SymbolicMapImpl<K, V> implements SymbolicMap<K, V> {
    private final Map<K, V> data = new HashMap<>();

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public V get(K key) {
        return data.get(key);
    }

    @Override
    public K anyKey() {
        return data.keySet().stream().findAny().get();
    }

    @Override
    public void set(K key, V value) {
        data.put(key, value);
    }

    @Override
    public void remove(K key) {
        data.remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return data.containsKey(key);
    }

    @Override
    public void merge(SymbolicMap<K, V> src) {
        SymbolicMapImpl<K, V> srcImpl = (SymbolicMapImpl<K, V>) src;
        data.putAll(srcImpl.data);
    }

    @Override
    public Object[] entries() {
        return data.entrySet().toArray();
    }
}
