package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.types.firstOrNull
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

/** Builds constraint-level execution plans directly from a TypeScript symbolic state. */
fun interface TsIntrinsicUnknownCallModel {
    fun execute(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution
}

/** Opaque registry handle for a Kotlin intrinsic semantic model. */
class TsIntrinsicUnknownCallModelImplementation(
    val model: TsIntrinsicUnknownCallModel,
) : TsUnknownCallModelImplementation {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.INTRINSIC
}

/** Executes intrinsic model handles without exposing them to the common registry or dispatcher contract. */
object TsIntrinsicUnknownCallModelBackend : TsUnknownCallModelBackend {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.INTRINSIC

    override fun execute(
        implementation: TsUnknownCallModelImplementation,
        state: TsState,
        call: TsUnknownCall,
    ): TsUnknownCallModelExecution {
        val intrinsic = requireNotNull(implementation as? TsIntrinsicUnknownCallModelImplementation) {
            "INTRINSIC backend requires TsIntrinsicUnknownCallModelImplementation, got ${implementation::class}"
        }

        return intrinsic.model.execute(state = state, call = call)
    }
}

/** The intentionally small built-in catalog enabled by default for profile-based unknown-call dispatch. */
object TsBuiltInUnknownCallModels {
    const val ARRAY_POP_MODEL_ID: String = "ts.array.pop"

    private val arrayPopDescriptor = TsUnknownCallModelDescriptor(
        id = ARRAY_POP_MODEL_ID,
        matcher = TsUnknownCallModelMatcher { call ->
            call.failureReason == TsUnknownCallFailureReason.PARTIAL_APPROXIMATION &&
                call.callee.name == "pop"
        },
        supportedDomain = TsUnknownCallModelSupportedDomain(
            id = "native-array-pop",
            description = "Resolved one-dimensional native arrays with no arguments and a primitive element sort",
        ),
        precision = TsUnknownCallModelPrecision.PARTIAL,
        implementationKind = TsUnknownCallModelImplementationKind.INTRINSIC,
    )

    val registry = TsUnknownCallModelRegistry(
        registrations = listOf(
            TsUnknownCallModelRegistration(
                descriptor = arrayPopDescriptor,
                implementation = TsIntrinsicUnknownCallModelImplementation(TsArrayPopIntrinsicModel),
            ),
        ),
        backends = listOf(TsIntrinsicUnknownCallModelBackend),
    )
}

private object TsArrayPopIntrinsicModel : TsIntrinsicUnknownCallModel {
    override fun execute(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
        val input = resolveInput(state = state, call = call)
            ?: return unsupportedExecution(state)

        val lengthLValue = mkArrayLengthLValue(input.array, input.arrayType)
        val length = state.memory.read(lengthLValue)
        val zero = state.ctx.mkBv(0)
        val emptyGuard = state.ctx.mkEq(length, zero)
        val nonEmptyGuard = state.ctx.mkBvSignedLessExpr(zero, length)
        val residualGuard = state.ctx.mkNot(state.ctx.mkOr(emptyGuard, nonEmptyGuard))
        val newLength = state.ctx.mkBvSubExpr(length, state.ctx.mkBv(1))
        val lastElementLValue = mkArrayIndexLValue(
            sort = input.elementSort,
            ref = input.array,
            index = newLength,
            type = input.arrayType,
        )

        val emptySuccessor = TsUnknownCallModelSuccessor(
            guard = emptyGuard,
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )
        val nonEmptySuccessor = TsUnknownCallModelSuccessor(
            guard = nonEmptyGuard,
            completion = TsUnknownCallModelCompletion.Normal { memory.read(lastElementLValue) },
            applyStateChanges = {
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)
            },
        )

        return TsUnknownCallModelExecution(
            successors = listOf(emptySuccessor, nonEmptySuccessor),
            residualGuard = residualGuard,
        )
    }

    private fun resolveInput(state: TsState, call: TsUnknownCall): ArrayPopInput? {
        if (call.arguments.isNotEmpty()) {
            return null
        }

        val receiverValue = call.receiver?.resolved ?: return null
        if (receiverValue.sort != state.ctx.addressSort) {
            return null
        }

        val array = receiverValue.asExpr(state.ctx.addressSort)
        val sourceType = requireNotNull(call.receiver).source.type
        val memoryType = state.memory.typeStreamOf(array).firstOrNull()
        val arrayType = sequenceOf(memoryType, sourceType)
            .mapNotNull { it as? EtsArrayType }
            .firstOrNull { candidate ->
                candidate.dimensions == 1 && state.ctx.typeToSort(candidate.elementType) !is TsUnresolvedSort
            }
            ?: return null

        val elementSort = state.ctx.typeToSort(arrayType.elementType)
        if (elementSort == state.ctx.addressSort) {
            return null
        }

        return ArrayPopInput(
            array = array,
            arrayType = arrayType,
            elementSort = elementSort,
        )
    }

    private fun unsupportedExecution(state: TsState): TsUnknownCallModelExecution {
        val unreachableSuccessor = TsUnknownCallModelSuccessor(
            guard = state.ctx.falseExpr,
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )

        return TsUnknownCallModelExecution(
            successors = listOf(unreachableSuccessor),
            residualGuard = state.ctx.trueExpr,
        )
    }

    private class ArrayPopInput(
        val array: UExpr<UAddressSort>,
        val arrayType: EtsArrayType,
        val elementSort: USort,
    )
}
