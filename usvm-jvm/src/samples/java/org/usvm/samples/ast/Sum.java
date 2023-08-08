package org.usvm.samples.ast;

public class Sum extends Binary {
    public Sum(Ast left, Ast right) {
        super(left, right);
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
