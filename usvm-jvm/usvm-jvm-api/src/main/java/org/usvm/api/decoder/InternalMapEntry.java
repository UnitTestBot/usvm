package org.usvm.api.decoder;

import java.util.Map;

public class InternalMapEntry<K, V> implements Map.Entry<K, V> {

    private V value;

    public InternalMapEntry(V value) {
        this.value = value;
    }

    @Override
    public K getKey() {
        return null;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        V oldValue = this.value;
        this.value = value;
        return oldValue;
    }
}
