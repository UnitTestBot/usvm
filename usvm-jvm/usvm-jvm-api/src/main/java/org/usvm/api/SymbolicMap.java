package org.usvm.api;

public interface SymbolicMap<K, V> {
    int size();

    V get(K key);

    K anyKey();

    void set(K key, V value);

    void remove(K key);

    boolean containsKey(K key);

    void merge(SymbolicMap<K, V> src);

    Object[] entries();
}
