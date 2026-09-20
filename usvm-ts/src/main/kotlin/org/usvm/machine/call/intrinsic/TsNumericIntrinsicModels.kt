package org.usvm.machine.call.intrinsic

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsLocal
import org.usvm.UExpr
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.state.TsState

internal object TsNumericIntrinsicModelFamily : TsBuiltInUnknownCallModelFamily {
    const val MATH_ABS_ID: String = "ts.math.abs"
    const val MATH_CEIL_ID: String = "ts.math.ceil"
    const val MATH_MAX_ID: String = "ts.math.max"
    const val MATH_MIN_ID: String = "ts.math.min"
    const val MATH_ROUND_ID: String = "ts.math.round"
    const val NUMBER_IS_INTEGER_ID: String = "ts.number.isInteger"

    override val models: List<TsUnknownCallModel> = listOf(
        NumericIntrinsicModel(
            id = MATH_ABS_ID,
            methodName = "abs",
            implementation = { state, call ->
                unaryMathCall(state, call) { value -> state.ctx.mkFpAbsExpr(value) }
            },
        ),
        NumericIntrinsicModel(
            id = MATH_CEIL_ID,
            methodName = "ceil",
            implementation = { state, call ->
                unaryMathCall(state, call) { value ->
                    with(state.ctx) {
                        mkFpRoundToIntegralExpr(
                            roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardPositive),
                            value = value,
                        )
                    }
                }
            },
        ),
        NumericIntrinsicModel(
            id = MATH_MAX_ID,
            methodName = "max",
            implementation = { state, call ->
                variadicMathCall(
                    state = state,
                    call = call,
                    identity = Double.NEGATIVE_INFINITY,
                    combine = state::mathMax,
                )
            },
        ),
        NumericIntrinsicModel(
            id = MATH_MIN_ID,
            methodName = "min",
            implementation = { state, call ->
                variadicMathCall(
                    state = state,
                    call = call,
                    identity = Double.POSITIVE_INFINITY,
                    combine = state::mathMin,
                )
            },
        ),
        NumericIntrinsicModel(
            id = MATH_ROUND_ID,
            methodName = "round",
            implementation = { state, call -> unaryMathCall(state, call, state::mathRound) },
        ),
        NumericIntrinsicModel(
            id = NUMBER_IS_INTEGER_ID,
            methodName = "isInteger",
            implementation = ::numberIsInteger,
        ),
    )
}

private class NumericIntrinsicModel(
    override val id: String,
    methodName: String,
    private val implementation: (TsState, TsUnknownCall) -> TsUnknownCallModelExecution?,
) : TsUnknownCallModel {
    override val target = numericTarget(methodName)

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? =
        implementation(state, call)
}

private fun numberIsInteger(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
    if (!call.hasGlobalOwner("Number")) {
        return null
    }
    val value = call.arguments.firstOrNull()?.resolved
        ?: return state.normalExecution(state.ctx.falseExpr)
    val result = with(state.ctx) {
        if (value.isFakeObject()) {
            val type = value.getFakeType(state.memory)
            mkAnd(type.fpTypeExpr, state.isInteger(value.extractFp(state.memory)))
        } else if (value.sort == fp64Sort) {
            state.isInteger(value.asExpr(fp64Sort))
        } else {
            falseExpr
        }
    }

    return state.normalExecution(result)
}

private fun numericTarget(methodName: String) = TsUnknownCallTarget(
    methodName = methodName,
    failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
)

private fun unaryMathCall(
    state: TsState,
    call: TsUnknownCall,
    operation: (UExpr<KFp64Sort>) -> UExpr<KFp64Sort>,
): TsUnknownCallModelExecution? {
    if (!call.hasGlobalOwner("Math")) {
        return null
    }
    val argument = call.arguments.firstOrNull()?.resolved
        ?: return state.normalExecution(state.ctx.mkFp(Double.NaN, state.ctx.fp64Sort))
    if (argument.sort != state.ctx.fp64Sort) {
        return null
    }

    val result = operation(argument.asExpr(state.ctx.fp64Sort))
    return state.normalExecution(result)
}

private fun variadicMathCall(
    state: TsState,
    call: TsUnknownCall,
    identity: Double,
    combine: (UExpr<KFp64Sort>, UExpr<KFp64Sort>) -> UExpr<KFp64Sort>,
): TsUnknownCallModelExecution? {
    if (!call.hasGlobalOwner("Math")) {
        return null
    }
    val arguments = call.arguments.map { argument ->
        val value = argument.resolved ?: return null
        if (value.sort != state.ctx.fp64Sort) {
            return null
        }

        value.asExpr(state.ctx.fp64Sort)
    }
    val result = arguments.fold(state.ctx.mkFp(identity, state.ctx.fp64Sort), combine)

    return state.normalExecution(result)
}

private fun TsUnknownCall.hasGlobalOwner(expectedName: String): Boolean {
    val owner = receiver?.source as? EtsLocal ?: return false
    return owner.name == expectedName
}

private fun TsState.normalExecution(result: UExpr<*>): TsUnknownCallModelExecution = with(ctx) {
    val successor = TsUnknownCallModelSuccessor(
        guard = trueExpr,
        completion = TsUnknownCallModelCompletion.Normal { result },
    )

    TsUnknownCallModelExecution(successors = listOf(successor))
}

private fun TsState.isInteger(value: UExpr<KFp64Sort>) = with(ctx) {
    val truncated = mkFpRoundToIntegralExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
        value = value,
    )

    mkAnd(
        mkFpIsNaNExpr(value).not(),
        mkFpIsInfiniteExpr(value).not(),
        mkFpEqualExpr(value, truncated),
    )
}

private fun TsState.mathMin(
    left: UExpr<KFp64Sort>,
    right: UExpr<KFp64Sort>,
): UExpr<KFp64Sort> = with(ctx) {
    val zero = mkFp(0.0, fp64Sort)
    val negativeZero = mkFp(-0.0, fp64Sort)
    val eitherNegative = mkOr(mkFpIsNegativeExpr(left), mkFpIsNegativeExpr(right))
    val signedZero = mkIte(eitherNegative, negativeZero, zero)
    val bothZero = mkAnd(mkFpIsZeroExpr(left), mkFpIsZeroExpr(right))

    mkIte(
        mkFpIsNaNExpr(left),
        left,
        mkIte(
            mkFpIsNaNExpr(right),
            right,
            mkIte(
                mkFpLessExpr(left, right),
                left,
                mkIte(mkFpLessExpr(right, left), right, mkIte(bothZero, signedZero, left)),
            ),
        ),
    )
}

private fun TsState.mathMax(
    left: UExpr<KFp64Sort>,
    right: UExpr<KFp64Sort>,
): UExpr<KFp64Sort> = with(ctx) {
    val zero = mkFp(0.0, fp64Sort)
    val negativeZero = mkFp(-0.0, fp64Sort)
    val eitherPositive = mkOr(mkFpIsPositiveExpr(left), mkFpIsPositiveExpr(right))
    val signedZero = mkIte(eitherPositive, zero, negativeZero)
    val bothZero = mkAnd(mkFpIsZeroExpr(left), mkFpIsZeroExpr(right))

    mkIte(
        mkFpIsNaNExpr(left),
        left,
        mkIte(
            mkFpIsNaNExpr(right),
            right,
            mkIte(
                mkFpGreaterExpr(left, right),
                left,
                mkIte(mkFpGreaterExpr(right, left), right, mkIte(bothZero, signedZero, left)),
            ),
        ),
    )
}

private fun TsState.mathRound(value: UExpr<KFp64Sort>): UExpr<KFp64Sort> = with(ctx) {
    val roundingMode = fpRoundingModeSortDefaultValue()
    val floor = mkFpRoundToIntegralExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardNegative),
        value = value,
    )
    val fraction = mkFpSubExpr(roundingMode, value, floor)
    val rounded = mkIte(
        mkFpLessExpr(fraction, mkFp(0.5, fp64Sort)),
        floor,
        mkFpAddExpr(roundingMode, floor, mkFp(1.0, fp64Sort)),
    )
    val signedRounded = mkIte(
        mkAnd(mkFpIsNegativeExpr(value), mkFpIsZeroExpr(rounded)),
        mkFp(-0.0, fp64Sort),
        rounded,
    )
    val preserveInput = mkOr(
        mkFpIsNaNExpr(value),
        mkFpIsInfiniteExpr(value),
        mkFpIsZeroExpr(value),
    )

    mkIte(preserveInput, value, signedRounded)
}
