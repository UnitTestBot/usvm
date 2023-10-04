package org.usvm.programs

import org.usvm.language.ArrayType
import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.builders.ProgramDecl
import org.usvm.language.builders.div
import org.usvm.language.builders.eq
import org.usvm.language.builders.expr
import org.usvm.language.builders.get
import org.usvm.language.builders.lt
import org.usvm.language.builders.method
import org.usvm.language.builders.plus
import org.usvm.language.builders.rem

object MergingProgram : ProgramDecl() {
    val ifMerging by method(BooleanType, returnType = IntType) { a ->
        var flag by false.expr
        branch(a) {
            flag = true.expr
        }

        branch(flag) {
            ret(1.expr)
        }
        ret(0.expr)
    }

    val ifLongMerging by method(IntType, returnType = IntType) { x ->
        var res by 0.expr
        branch(x % 2.expr eq 0.expr) {
            res += 1.expr
        }
        branch((x / 2.expr) % 2.expr eq 0.expr) {
            res += 2.expr
        }
        branch((x / 4.expr) % 2.expr eq 0.expr) {
            res += 4.expr
        }
        branch((x / 8.expr) % 2.expr eq 0.expr) {
            res += 8.expr
        }
        branch((x / 16.expr) % 2.expr eq 0.expr) {
            res += 16.expr
        }

        ret(res)
    }


    val loopMerging by method(ArrayType(BooleanType), IntType, returnType = IntType) { a, b ->
        var idx by 0.expr
        var flag by false.expr
        loop(idx lt b) {
            branch(a[idx]) {
                flag = true.expr
            }
            idx += 1.expr
        }

        branch(flag) {
            ret(1.expr)
        }
        ret(0.expr)
    }
}