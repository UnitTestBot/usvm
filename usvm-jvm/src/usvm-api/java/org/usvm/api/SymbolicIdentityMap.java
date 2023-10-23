package org.usvm.api;

public interface SymbolicIdentityMap<K, V> {
    int size();

    V get(K key);

    K anyKey();

    void set(K key, V value);

    void remove(K key);

    boolean containsKey(K key);

    void merge(SymbolicIdentityMap<K, V> src);
}
