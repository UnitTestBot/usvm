package org.usvm.programs

import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.builders.ProgramDecl
import org.usvm.language.builders.div
import org.usvm.language.builders.expr
import org.usvm.language.builders.lt
import org.usvm.language.builders.method
import org.usvm.language.builders.unaryMinus

object MathProgram : ProgramDecl() {
    val division by method(IntType, IntType, returnType = IntType) { a, b ->
        ret(a / b)
    }

    val abs by method(IntType, returnType = IntType) { a ->
        var res by a
        branch(res lt 0.expr) {
            res = -res
        }
        ret(res)
    }

    val absOverflow by method(IntType, returnType = BooleanType) { a ->
        val res by abs(a)
        branch(res lt 0.expr) {
            ret(false.expr)
        }
        ret(true.expr)
    }
}