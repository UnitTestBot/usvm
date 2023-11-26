package org.usvm.programs

import org.usvm.language.ArrayType
import org.usvm.language.IntType
import org.usvm.language.builders.*

object LoopProgram : ProgramDecl() {
    private const val CONST = 1_000

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

    val loopSimple by method(IntType, returnType = IntType) {
        var idx by 0.expr
        loop(idx lt 10.expr) {
            idx += 1.expr
        }
        ret(0.expr)
    }

    val loopHard by method(IntType, IntType, returnType = IntType) { i, j ->
        var sum by 0.expr
        var k by 0.expr
        loop(k lt 100.expr) {
            var l by 0.expr
            loop(l lt 100.expr) {
                branch((k + l) lt (i + j)) {
                    sum += (k + l)
                }
                l += 1.expr
            }
            k += 1.expr
        }
        ret(sum)
    }

    val loopInfinite by method(IntType, returnType = IntType) { i ->
        loop(true.expr) {
            var j by i + 3.expr
            val k by j + i
            val l by k + j + 4.expr
            j = l + 2.expr
        }
    }

    val loopCollatz by method(IntType, returnType = IntType) { i ->
        branch((i le 0.expr) or (i ge 100.expr)) {
            ret(0.expr)
        }

        var j by i
        var loopCount by 0.expr

        loop((j eq 1.expr).not()) {
            val oldJ by j
            j = oldJ * 3.expr + 1.expr
            branch((oldJ % 2.expr) eq 0.expr) {
                j = oldJ / 2.expr
            }
            loopCount += 1.expr
        }

        branch(loopCount eq 17.expr) {
            ret(1.expr)
        }
        ret(2.expr)
    }
}