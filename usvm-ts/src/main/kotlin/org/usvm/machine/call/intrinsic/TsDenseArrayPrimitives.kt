package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArray
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.mkFpToUint32AfterValidation
import org.usvm.machine.expr.mkValidArrayLength
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.hasDenseArrayShape
import org.usvm.util.markDenseInputArray
import org.usvm.util.mkArrayLengthLValue

/** Memory and primitive-value operations used by the source split/join/reduce algorithms. */
internal object TsDenseArrayPrimitives : TsBuiltInUnknownCallModelFamily {
    private const val CAPACITY = 16
    private val stringsType = EtsArrayType(EtsStringType, dimensions = 1)

    override val models: List<TsUnknownCallModel> = listOf(
        Primitive("allocateStrings", arity = 1, implementation = ::allocateStrings),
        Primitive("truncateDense", arity = 2, implementation = ::truncateDense),
        Primitive("requireDense", arity = 1, implementation = ::requireDense),
        Primitive("elementString", arity = 1, implementation = ::elementString),
    )

    private fun allocateStrings(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
        with(state.ctx) {
            // All capacity slots are initialized, so subsequent in-range writes preserve density.
            if (inputs.single() != mkFp64(CAPACITY.toDouble())) return null
            execution(guard = trueExpr) {
                val descriptor = ctx.arrayDescriptorOf(stringsType)
                val array = memory.allocConcrete(stringsType)
                val empty = mkInitializedStringConstant("")
                memory.initializeArray(
                    arrayHeapRef = array,
                    type = descriptor,
                    sort = ctx.addressSort,
                    sizeSort = ctx.sizeSort,
                    contents = List(CAPACITY) { empty }.asSequence(),
                )
                markDenseInputArray(array = array, type = stringsType)
                array
            }
        }

    private fun truncateDense(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
        with(state.ctx) {
            val array = inputs[0] as? UConcreteHeapRef ?: return null
            if (!state.hasDenseArrayShape(array, stringsType)) return null
            val length = inputs[1].takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
            val validLength = mkValidArrayLength(length)
            val converted = mkFpToUint32AfterValidation(length, validLength).asExpr(sizeSort)
            val lengthLocation = mkArrayLengthLValue(array, stringsType)
            val oldLength = state.memory.read(lengthLocation)
            val guard = mkAnd(validLength, mkBvUnsignedLessOrEqualExpr(converted, oldLength))
            execution(guard = guard) {
                memory.write(lengthLocation, converted, guard = ctx.trueExpr)
                markDenseInputArray(array = array, type = stringsType)
                array
            }
        }

    private fun requireDense(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? {
        val array = inputs.single() as? UConcreteHeapRef ?: return null
        val type = state.arrayStorageType(array, EtsArrayType(EtsUnknownType, dimensions = 1)) as? EtsArrayType
            ?: return null
        if (!state.hasDenseArrayShape(array, type)) return null
        return execution(guard = state.ctx.trueExpr) { ctx.mkUndefinedValue() }
    }

    private fun elementString(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
        with(state.ctx) {
            val value = inputs.single()
            if (value.isFakeObject()) {
                // Only reference payloads are admitted here; numeric/object coercion stays residual.
                val kind = value.getFakeType(state.memory)
                val ref = value.extractRef(state.memory)
                if (state.hasUnsafeStringReference(ref)) return null
                val isString = state.memory.types.evalTypeEquals(ref, EtsStringType)
                val nullish = mkOr(mkEq(ref, mkUndefinedValue()), mkEq(ref, nullRef))
                val guard = mkAnd(kind.refTypeExpr, mkOr(isString, nullish))
                return execution(guard = guard) {
                    ctx.mkIte(nullish, mkInitializedStringConstant(""), ref)
                }
            }
            when {
                value.sort == boolSort -> execution(guard = trueExpr) {
                    ctx.mkIte(
                        value.asExpr(ctx.boolSort),
                        mkInitializedStringConstant("true"),
                        mkInitializedStringConstant("false"),
                    )
                }
                value.sort == addressSort -> {
                    val ref = value.asExpr(addressSort)
                    if (state.hasUnsafeStringReference(ref)) return null
                    val nullish = mkOr(mkEq(ref, mkUndefinedValue()), mkEq(ref, nullRef))
                    val guard = mkOr(nullish, state.memory.types.evalTypeEquals(ref, EtsStringType))
                    execution(guard = guard) { ctx.mkIte(nullish, mkInitializedStringConstant(""), ref) }
                }
                else -> null
            }
        }

    private fun TsState.hasUnsafeStringReference(ref: UHeapRef): Boolean = with(ctx) {
        when {
            ref.hasFakeValueBranch() -> true
            ref is UConcreteHeapRef -> ref in associatedFunction
            ref is UIteExpr<*> -> hasUnsafeStringReference(ref.trueBranch.asExpr(addressSort)) ||
                hasUnsafeStringReference(ref.falseBranch.asExpr(addressSort))
            else -> false
        }
    }

    private fun execution(guard: UBoolExpr, result: TsState.() -> UExpr<*>): TsUnknownCallModelExecution {
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal(result),
        )
        return TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = with(guard.ctx) { mkNot(guard) },
        )
    }

    private class Primitive(
        methodName: String,
        private val arity: Int,
        private val implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ) : TsUnknownCallModel {
        override val id = "ts.array.primitive.$methodName"
        override val target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = "ArrayModelPrimitives",
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
        )

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            if (call.receiver != null || call.arguments.size != arity) return null
            return implementation(state, call.arguments.map { it.resolved ?: return null })
        }
    }
}
