package org.usvm.samples.ast;

public class Minus extends Binary {
    public Minus(Ast left, Ast right) {
        super(left, right);
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
