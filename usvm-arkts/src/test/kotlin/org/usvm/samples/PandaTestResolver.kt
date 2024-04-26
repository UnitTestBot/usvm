package org.usvm.samples

import io.ksmt.expr.KFp64Value
import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.asExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaPrimitiveType
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.isTrue
import org.usvm.machine.PandaTest
import org.usvm.machine.pctx
import org.usvm.machine.state.PandaMethodResult
import org.usvm.machine.state.PandaState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.types.first

class PandaTestResolver {
    lateinit var model : UModelBase<PandaType>
    lateinit var memory: UReadOnlyMemory<PandaType>

    fun resolve(method: PandaMethod, state: PandaState): PandaTest {
        val ctx = state.ctx

        model = state.models.single()
        memory = state.memory

        val parameters = method.parameters.mapIndexed { idx, param ->
            val ref = URegisterStackLValue(ctx.addressSort, idx) // TODO address sort?
            resolveLValue(ref, model)
        }

        val resultExpr = resolveExpr((state.methodResult as PandaMethodResult.Success).value)

        return PandaTest(parameters, resultExpr)
    }

    private fun resolveLValue(lValue: ULValue<*,*>, memory: UReadOnlyMemory<PandaType>) : Any {
        val expr = memory.read(lValue)

        return resolveExpr(expr)
    }

    // TODO extract memory
    private fun resolveExpr(expr: UExpr<out USort>): Any {
        val pctx = expr.pctx

        if (expr.sort != pctx.addressSort) {
            return resolveInterpreterValue(model.eval(expr) as KInterpretedValue)
        }

        val ref = expr.asExpr(pctx.addressSort)
        val concreteRef = model.eval(ref)

        val type = model.typeStreamOf(concreteRef).first()

        if (type is PandaPrimitiveType) {
            val lvalue = pctx.constructAuxiliaryFieldLValue(concreteRef, pctx.typeToSort(type))
            val exprFromMemory = memory.read(lvalue)

            return model.eval(exprFromMemory)
        }

        TODO()
    }

    private fun resolveInterpreterValue(expr: KInterpretedValue<out USort>): Any = with(expr.pctx) {
        val sort = expr.sort

        when (sort) {
            fp64Sort -> (expr as KFp64Value).value
            boolSort -> expr.asExpr(boolSort).isTrue
            stringSort -> TODO()
            else -> error("Should not be called")
        }
    }
}