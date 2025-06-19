package org.usvm.api.internal;

import org.usvm.api.SymbolicList;

import java.util.ArrayList;
import java.util.List;

public class SymbolicListImpl<E> implements SymbolicList<E> {
    private final List<E> data = new ArrayList<>();

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public E get(int idx) {
        return data.get(idx);
    }

    @Override
    public void set(int idx, E obj) {
        data.set(idx, obj);
    }

    @Override
    public void insert(int idx, E obj) {
        data.add(idx, obj);
    }

    @Override
    public void remove(int idx) {
        data.remove(idx);
    }

    @Override
    public void copy(SymbolicList<E> dst, int srcFrom, int dstFrom, int length) {
        SymbolicListImpl<E> dstImpl = (SymbolicListImpl<E>) dst;

        while (dstImpl.data.size() < dstFrom + length) {
            dstImpl.data.add(null);
        }

        for (int i = 0; i < length; i++) {
            E element = data.get(srcFrom + i);
            dstImpl.data.set(dstFrom + i, element);
        }
    }
}
