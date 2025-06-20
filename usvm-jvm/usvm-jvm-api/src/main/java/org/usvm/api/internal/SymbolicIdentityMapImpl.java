package org.usvm.api.internal;

import org.usvm.api.SymbolicIdentityMap;

import java.util.IdentityHashMap;
import java.util.Map;

public class SymbolicIdentityMapImpl<K, V> implements SymbolicIdentityMap<K, V> {
    private final Map<K, V> data = new IdentityHashMap<>();

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
    public void merge(SymbolicIdentityMap<K, V> src) {
        SymbolicIdentityMapImpl<K, V> srcImpl = (SymbolicIdentityMapImpl<K, V>) src;
        data.putAll(srcImpl.data);
    }

    @Override
    public Object[] entries() {
        return data.entrySet().toArray();
    }
}
