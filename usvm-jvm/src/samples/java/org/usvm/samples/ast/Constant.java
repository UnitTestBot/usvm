package org.usvm.samples.ast;

public class Constant implements Ast {
    final private int constant;

    public Constant(int constant) {
        this.constant = constant;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }

    public int getConstant() {
        return constant;
    }
}
