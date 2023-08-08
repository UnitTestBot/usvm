package org.usvm.samples.ast;

public interface Visitor<T> {
    T visit(Constant constant);

    T visit(Sum sum);

    T visit(Minus minus);

    T visit(Variable variable);
}
