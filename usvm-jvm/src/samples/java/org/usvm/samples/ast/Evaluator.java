package org.usvm.samples.ast;

public class Evaluator implements Visitor<Constant> {
    @Override
    public Constant visit(Constant constant) {
        return constant;
    }

    @Override
    public Constant visit(Sum sum) {
        Constant left = sum.getLeft().accept(this);
        Constant right = sum.getRight().accept(this);
        return new Constant(left.getConstant() + right.getConstant());
    }

    @Override
    public Constant visit(Minus minus) {
        Constant left = minus.getLeft().accept(this);
        Constant right = minus.getRight().accept(this);
        return new Constant(left.getConstant() - right.getConstant());
    }

    @Override
    public Constant visit(Variable variable) {
        throw new IllegalArgumentException();
    }
}
