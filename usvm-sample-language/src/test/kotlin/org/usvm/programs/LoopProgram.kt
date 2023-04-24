package org.usvm.programs

import org.usvm.language.ArrayType
import org.usvm.language.IntType
import org.usvm.language.builders.ProgramDecl
import org.usvm.language.builders.eq
import org.usvm.language.builders.expr
import org.usvm.language.builders.get
import org.usvm.language.builders.invoke
import org.usvm.language.builders.lt
import org.usvm.language.builders.method
import org.usvm.language.builders.plus

object LoopProgram : ProgramDecl() {
    const val CONST = 1_000

    val loopHighIdx by method(IntType, IntType, returnType = IntType) { _, a ->
        val arr by ArrayType(IntType)(size = CONST.expr)

        var idx by 0.expr
        loop(idx lt CONST.expr) {
            arr[idx] = idx

            idx += 1.expr
        }

        branch(arr[a] eq (CONST - 10).expr) {
            ret(1.expr)
        }
        ret(0.expr)
    }

    val loopLowIdx by method(IntType, IntType, returnType = IntType) { _, a ->
        val arr by ArrayType(IntType)(size = CONST.expr)

        var idx by 0.expr
        loop(idx lt CONST.expr) {
            arr[idx] = idx

            idx += 1.expr
        }

        branch(arr[a] eq 10.expr) {
            ret(1.expr)
        }
        ret(0.expr)
    }
}