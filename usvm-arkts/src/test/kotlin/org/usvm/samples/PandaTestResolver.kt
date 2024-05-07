package org.usvm.samples

import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.PandaTest
import org.usvm.machine.state.PandaMethodResult
import org.usvm.machine.state.PandaState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase

class PandaTestResolver(
    private val model: UModelBase<PandaType>,
    private val finalStateMemory: UReadOnlyMemory<PandaType>,
) {


    fun resolve(method: PandaMethod, state: PandaState): PandaTest {
        val ctx = state.ctx
        val parameters = method.parameters.mapIndexed { idx, param ->
            val ref = URegisterStackLValue(ctx.addressSort, idx) // TODO address sort?
            resolveLValue(ref)
        }

        val resultExpr = resolveExpr((state.methodResult as PandaMethodResult.Success).value)

        return PandaTest(parameters, resultExpr)
    }

    private fun resolveLValue(lValue: ULValue<*,*>) : Any {
        val expr = model.read(lValue)

        return resolveExpr(expr)
    }

    private fun resolveExpr(expr: UExpr<out USort>): Any {
//        val type = model.types.getTypeStream(expr.asExpr(expr.pctx.addressSort)).first()

        return 1
    }
}