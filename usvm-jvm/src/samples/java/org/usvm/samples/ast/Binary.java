package org.usvm.samples.ast;

abstract class Binary implements Ast {
    private final Ast left, right;

    public Binary(Ast left, Ast right) {
        this.left = left;
        this.right = right;
    }

    public Ast getLeft() {
        return left;
    }

    public Ast getRight() {
        return right;
    }
}
