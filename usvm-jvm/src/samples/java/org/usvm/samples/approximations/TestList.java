package org.usvm.samples.approximations;

import java.util.ArrayList;
import java.util.List;

public class TestList<T> {
    private final List<T> list = new ArrayList<>();

    public int size() {
        return list.size();
    }

    public void add(T value) {
        list.add(value);
    }

    public T get(int idx) {
        return list.get(idx);
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
