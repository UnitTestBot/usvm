package org.usvm.samples.ast;

public class AstExample {
    public int replaceLeafAndCheck(Ast leaf) {
        DefaultSubstitutor substitutor = new DefaultSubstitutor(0);
        Ast ast = new Sum(
                new Minus(
                        new Constant(13),
                        new Constant(-16)
                ),
                new Minus(
                        new Constant(13),
                        leaf
                )
        );
        Ast substituted = ast.accept(substitutor);
        Evaluator evaluator = new Evaluator();
        Constant result = substituted.accept(evaluator);

        // (13 - (-16)) + (13 - leaf) == 0
        if (result.getConstant() == 0) {
            // too easy
            if (leaf instanceof Constant) {
                return -1;
            }
            return 0;
        } else {
            return 1;
        }
    }
}
