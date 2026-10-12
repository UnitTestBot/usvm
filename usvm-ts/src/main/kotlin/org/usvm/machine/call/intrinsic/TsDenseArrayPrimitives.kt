package org.usvm.machine.call.intrinsic

import io.ksmt.expr.KFp64Value
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
import org.usvm.isFalse
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.expr.mkFpToUint32AfterValidation
import org.usvm.machine.expr.mkValidArrayLength
import org.usvm.machine.expr.toEcmaScriptString
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.hasDenseArrayShape
import org.usvm.util.markDenseInputArray
import org.usvm.util.mkArrayLengthLValue

/** Memory and primitive-value operations used by the source split/join/reduce algorithms. */
internal object TsDenseArrayPrimitives {
    private val stringsType = EtsArrayType(EtsStringType, dimensions = 1)

    fun allocateStrings(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
        with(state.ctx) {
            // All capacity slots are initialized, so subsequent in-range writes preserve density.
            if (inputs.single() != mkFp64(SOURCE_ARRAY_MODEL_CAPACITY.toDouble())) return null
            execution(guard = trueExpr) {
                val descriptor = ctx.arrayDescriptorOf(stringsType)
                val array = memory.allocConcrete(stringsType)
                val empty = mkInitializedStringConstant("")
                memory.initializeArray(
                    arrayHeapRef = array,
                    type = descriptor,
                    sort = ctx.addressSort,
                    sizeSort = ctx.sizeSort,
                    contents = List(SOURCE_ARRAY_MODEL_CAPACITY) { empty }.asSequence(),
                )
                markDenseInputArray(array = array, type = stringsType)
                array
            }
        }

    fun truncateDense(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
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

    fun requireDense(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? {
        val array = inputs.single() as? UConcreteHeapRef ?: return null
        val type = state.arrayStorageType(array, EtsArrayType(EtsUnknownType, dimensions = 1)) as? EtsArrayType
            ?: return null
        if (!state.hasDenseArrayShape(array, type)) return null
        return execution(guard = state.ctx.trueExpr) { ctx.mkUndefinedValue() }
    }

    fun elementString(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? =
        with(state.ctx) {
            val value = inputs.single()
            if (value.isFakeObject()) {
                val kind = value.getFakeType(state.memory)
                val ref = value.extractRef(state.memory)
                val nullish = mkOr(mkEq(ref, mkUndefinedValue()), mkEq(ref, mkTsNullValue()))
                val referenceGuard = if (kind.refTypeExpr.isFalse || state.hasUnsafeStringReference(ref)) {
                    falseExpr
                } else {
                    val isString = state.memory.types.evalTypeEquals(ref, EtsStringType)
                    mkAnd(kind.refTypeExpr, mkOr(isString, nullish))
                }
                val concreteNumber = if (kind.fpTypeExpr.isFalse) {
                    null
                } else {
                    value.extractFp(state.memory) as? KFp64Value
                }
                val numberText = concreteNumber?.value?.toEcmaScriptString()
                val numberGuard = if (numberText != null) kind.fpTypeExpr else falseExpr
                val successors = buildList {
                    if (!kind.boolTypeExpr.isFalse) {
                        val successor = TsUnknownCallModelSuccessor(
                            guard = kind.boolTypeExpr,
                            completion = TsUnknownCallModelCompletion.Normal {
                                booleanString(value.extractBool(memory))
                            },
                        )
                        add(successor)
                    }
                    if (!referenceGuard.isFalse) {
                        val successor = TsUnknownCallModelSuccessor(
                            guard = referenceGuard,
                            completion = TsUnknownCallModelCompletion.Normal {
                                ctx.mkIte(nullish, mkInitializedStringConstant(""), ref)
                            },
                        )
                        add(successor)
                    }
                    if (numberText != null && !numberGuard.isFalse) {
                        val successor = TsUnknownCallModelSuccessor(
                            guard = numberGuard,
                            completion = TsUnknownCallModelCompletion.Normal {
                                mkInitializedStringConstant(numberText)
                            },
                        )
                        add(successor)
                    }
                }
                if (successors.isEmpty()) return null

                val supportedGuard = mkOr(kind.boolTypeExpr, referenceGuard, numberGuard)
                return TsUnknownCallModelExecution(
                    successors = successors,
                    residualGuard = mkNot(supportedGuard),
                )
            }
            when {
                value is KFp64Value -> execution(guard = trueExpr) {
                    mkInitializedStringConstant(value.value.toEcmaScriptString())
                }
                value.sort == boolSort -> execution(guard = trueExpr) {
                    booleanString(value.asExpr(ctx.boolSort))
                }
                value.sort == addressSort -> {
                    val ref = value.asExpr(addressSort)
                    if (state.hasUnsafeStringReference(ref)) return null
                    val nullish = mkOr(mkEq(ref, mkUndefinedValue()), mkEq(ref, mkTsNullValue()))
                    val guard = mkOr(nullish, state.memory.types.evalTypeEquals(ref, EtsStringType))
                    execution(guard = guard) { ctx.mkIte(nullish, mkInitializedStringConstant(""), ref) }
                }
                else -> null
            }
        }

    private fun TsState.booleanString(value: UBoolExpr): UHeapRef = with(ctx) {
        mkIte(value, mkInitializedStringConstant("true"), mkInitializedStringConstant("false"))
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
}
