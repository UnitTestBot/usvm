package org.usvm.samples.ast;

public class Variable implements Ast {
    public final int id;

    public Variable(int id) {
        this.id = id;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
