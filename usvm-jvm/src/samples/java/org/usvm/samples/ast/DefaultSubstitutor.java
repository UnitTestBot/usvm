package org.usvm.samples.ast;

public class DefaultSubstitutor implements Visitor<Ast> {
    private final int defaultValue;

    public DefaultSubstitutor(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Ast visit(Constant constant) {
        return constant;
    }

    @Override
    public Ast visit(Sum sum) {
        Ast left = sum.getLeft().accept(this);
        Ast right = sum.getRight().accept(this);
        if (left == sum.getLeft() && right == sum.getRight()) {
            return sum;
        }
        return new Sum(left, right);
    }

    @Override
    public Ast visit(Minus minus) {
        Ast left = minus.getLeft().accept(this);
        Ast right = minus.getRight().accept(this);
        if (left == minus.getLeft() && right == minus.getRight()) {
            return minus;
        }
        return new Minus(left, right);
    }

    @Override
    public Ast visit(Variable variable) {
        return new Constant(defaultValue);
    }
}
