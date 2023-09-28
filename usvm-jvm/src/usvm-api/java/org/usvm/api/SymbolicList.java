package org.usvm.api;

public interface SymbolicList<E> {
    int size();

    E get(int idx);

    void set(int idx, E obj);

    void insert(int idx, E obj);

    void remove(int idx);

    void copy(SymbolicList<E> dst, int srcFrom, int dstFrom, int length);
}
