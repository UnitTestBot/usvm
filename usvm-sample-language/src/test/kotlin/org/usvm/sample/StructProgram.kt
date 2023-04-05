package org.usvm.sample

import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.dsl.ProgramDecl
import org.usvm.language.dsl.and
import org.usvm.language.dsl.eq
import org.usvm.language.dsl.expr
import org.usvm.language.dsl.get
import org.usvm.language.dsl.isNull
import org.usvm.language.dsl.method
import org.usvm.language.dsl.not
import org.usvm.language.dsl.plus

object StructProgram : ProgramDecl() {
    object S : StructDecl() {
        val f1 by IntType
        val f2 by BooleanType
    }

    val method2 by method(S.type, returnType = IntType) { inS ->
        branch(!inS.isNull) {
            ret(2.expr)
        }
        branch(inS[S.f2] eq (inS[S.f1] eq (-1).expr)) {
            ret(4.expr)
        }

        ret(3.expr)
    }

    val method3 by method(IntType, returnType = IntType) { a ->
        val test by S(S.f1 to 1.expr, S.f2 to false.expr)

        branch((test[S.f1] eq a) and (test[S.f2] eq a.eq(0.expr))) {
            ret(0.expr)
        }

        val test3 by S(S.f1 to a, S.f2 to true.expr)

        branch((test3[S.f1] + a) eq 10.expr) {
            ret(1.expr)
        }

        ret(method2(test3))
    }


}