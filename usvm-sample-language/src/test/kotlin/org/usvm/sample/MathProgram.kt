package org.usvm.sample

import org.usvm.language.ArrayType
import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.dsl.ProgramDecl
import org.usvm.language.dsl.div
import org.usvm.language.dsl.eq
import org.usvm.language.dsl.expr
import org.usvm.language.dsl.get
import org.usvm.language.dsl.invoke
import org.usvm.language.dsl.le
import org.usvm.language.dsl.lt
import org.usvm.language.dsl.method
import org.usvm.language.dsl.unaryMinus

object AbsProgram : ProgramDecl() {
    val m1 by method(IntType, returnType = IntType) { a ->
        var res by a
        branch(res lt 0.expr) {
            res = -res
        }
        ret(res)
    }

    val m2 by method(IntType, IntType, BooleanType, returnType = IntType) { a, b, xr ->
        branch((a eq b) eq xr) {
            ret(0.expr)
        }
        ret(1.expr)
    }

    val m3 by method(IntType, IntType, returnType = IntType) { a, b ->
        ret(a / b)
    }

    val m4 by method(IntType, returnType = IntType) { idx ->
        val arr by ArrayType(IntType)(0.expr, 1.expr)
        branch(idx le 0.expr) {
            ret(3.expr)
        }
        ret(arr[idx])
    }

}