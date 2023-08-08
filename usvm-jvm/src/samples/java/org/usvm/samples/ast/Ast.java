package org.usvm.samples.ast;

public interface Ast {
    <T> T accept(Visitor<T> visitor);
}
