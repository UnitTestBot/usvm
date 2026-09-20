package org.usvm.machine.call.intrinsic

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsLocal
import org.usvm.UBoolExpr
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
    const val MATH_FLOOR_ID: String = "ts.math.floor"
    const val MATH_MAX_ID: String = "ts.math.max"
    const val MATH_MIN_ID: String = "ts.math.min"
    const val MATH_ROUND_ID: String = "ts.math.round"
    const val MATH_SQRT_ID: String = "ts.math.sqrt"
    const val MATH_TRUNC_ID: String = "ts.math.trunc"
    const val NUMBER_IS_FINITE_ID: String = "ts.number.isFinite"
    const val NUMBER_IS_INTEGER_ID: String = "ts.number.isInteger"
    const val NUMBER_IS_NAN_ID: String = "ts.number.isNaN"
    const val NUMBER_IS_SAFE_INTEGER_ID: String = "ts.number.isSafeInteger"

    private val mathAbsModel = NumericIntrinsicModel(
        id = MATH_ABS_ID,
        methodName = "abs",
        implementation = { state, call ->
            unaryMathCall(state, call) { value -> state.ctx.mkFpAbsExpr(value) }
        },
    )
    private val mathCeilModel = NumericIntrinsicModel(
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
    )
    private val mathFloorModel = NumericIntrinsicModel(
        id = MATH_FLOOR_ID,
        methodName = "floor",
        implementation = roundingMathCall(roundingMode = KFpRoundingMode.RoundTowardNegative),
    )
    private val mathMaxModel = NumericIntrinsicModel(
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
    )
    private val mathMinModel = NumericIntrinsicModel(
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
    )
    private val mathRoundModel = NumericIntrinsicModel(
        id = MATH_ROUND_ID,
        methodName = "round",
        implementation = { state, call -> unaryMathCall(state, call, state::mathRound) },
    )
    private val mathSqrtModel = NumericIntrinsicModel(
        id = MATH_SQRT_ID,
        methodName = "sqrt",
        implementation = { state, call ->
            unaryMathCall(state, call) { value ->
                state.ctx.mkFpSqrtExpr(state.ctx.fpRoundingModeSortDefaultValue(), value)
            }
        },
    )
    private val mathTruncModel = NumericIntrinsicModel(
        id = MATH_TRUNC_ID,
        methodName = "trunc",
        implementation = roundingMathCall(roundingMode = KFpRoundingMode.RoundTowardZero),
    )
    private val numberIsFiniteModel = NumericIntrinsicModel(
        id = NUMBER_IS_FINITE_ID,
        methodName = "isFinite",
        implementation = { state, call ->
            numberPredicate(state, call) { value ->
                with(state.ctx) {
                    val isNotNaN = mkFpIsNaNExpr(value).not()
                    val isNotInfinite = mkFpIsInfiniteExpr(value).not()

                    mkAnd(isNotNaN, isNotInfinite)
                }
            }
        },
    )
    private val numberIsIntegerModel = NumericIntrinsicModel(
        id = NUMBER_IS_INTEGER_ID,
        methodName = "isInteger",
        implementation = { state, call -> numberPredicate(state, call, state::isInteger) },
    )
    private val numberIsNaNModel = NumericIntrinsicModel(
        id = NUMBER_IS_NAN_ID,
        methodName = "isNaN",
        implementation = { state, call ->
            numberPredicate(state, call) { value -> state.ctx.mkFpIsNaNExpr(value) }
        },
    )
    private val numberIsSafeIntegerModel = NumericIntrinsicModel(
        id = NUMBER_IS_SAFE_INTEGER_ID,
        methodName = "isSafeInteger",
        implementation = { state, call -> numberPredicate(state, call, state::isSafeInteger) },
    )

    override val models: List<TsUnknownCallModel> = listOf(
        mathAbsModel,
        mathCeilModel,
        mathFloorModel,
        mathMaxModel,
        mathMinModel,
        mathRoundModel,
        mathSqrtModel,
        mathTruncModel,
        numberIsFiniteModel,
        numberIsIntegerModel,
        numberIsNaNModel,
        numberIsSafeIntegerModel,
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

private fun numberPredicate(
    state: TsState,
    call: TsUnknownCall,
    predicate: (UExpr<KFp64Sort>) -> UBoolExpr,
): TsUnknownCallModelExecution? {
    if (!call.hasGlobalOwner("Number")) {
        return null
    }
    val value = call.arguments.firstOrNull()?.resolved
        ?: return state.normalExecution(state.ctx.falseExpr)
    val result = with(state.ctx) {
        if (value.isFakeObject()) {
            val type = value.getFakeType(state.memory)
            mkAnd(type.fpTypeExpr, predicate(value.extractFp(state.memory)))
        } else if (value.sort == fp64Sort) {
            predicate(value.asExpr(fp64Sort))
        } else {
            falseExpr
        }
    }

    return state.normalExecution(result)
}

private fun roundingMathCall(
    roundingMode: KFpRoundingMode,
): (TsState, TsUnknownCall) -> TsUnknownCallModelExecution? = { state, call ->
    unaryMathCall(state, call) { value ->
        with(state.ctx) {
            mkFpRoundToIntegralExpr(
                roundingMode = mkFpRoundingModeExpr(roundingMode),
                value = value,
            )
        }
    }
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

private fun TsState.isSafeInteger(value: UExpr<KFp64Sort>) = with(ctx) {
    val absoluteValue = mkFpAbsExpr(value)
    val maxSafeInteger = mkFp(MAX_SAFE_INTEGER, fp64Sort)
    val isInSafeRange = mkFpLessOrEqualExpr(absoluteValue, maxSafeInteger)

    mkAnd(
        isInteger(value),
        isInSafeRange,
    )
}

private fun TsState.mathMin(
    left: UExpr<KFp64Sort>,
    right: UExpr<KFp64Sort>,
): UExpr<KFp64Sort> = with(ctx) {
    val zero = mkFp(0.0, fp64Sort)
    val negativeZero = mkFp(NEGATIVE_ZERO, fp64Sort)
    val leftIsNegative = mkFpIsNegativeExpr(left)
    val rightIsNegative = mkFpIsNegativeExpr(right)
    val eitherNegative = mkOr(leftIsNegative, rightIsNegative)
    val signedZero = mkIte(eitherNegative, negativeZero, zero)
    val leftIsZero = mkFpIsZeroExpr(left)
    val rightIsZero = mkFpIsZeroExpr(right)
    val bothZero = mkAnd(leftIsZero, rightIsZero)
    val equalResult = mkIte(bothZero, signedZero, left)
    val rightLessResult = mkIte(mkFpLessExpr(right, left), right, equalResult)
    val leftLessResult = mkIte(mkFpLessExpr(left, right), left, rightLessResult)
    val rightNaNResult = mkIte(mkFpIsNaNExpr(right), right, leftLessResult)

    mkIte(mkFpIsNaNExpr(left), left, rightNaNResult)
}

private fun TsState.mathMax(
    left: UExpr<KFp64Sort>,
    right: UExpr<KFp64Sort>,
): UExpr<KFp64Sort> = with(ctx) {
    val zero = mkFp(0.0, fp64Sort)
    val negativeZero = mkFp(NEGATIVE_ZERO, fp64Sort)
    val leftIsPositive = mkFpIsPositiveExpr(left)
    val rightIsPositive = mkFpIsPositiveExpr(right)
    val eitherPositive = mkOr(leftIsPositive, rightIsPositive)
    val signedZero = mkIte(eitherPositive, zero, negativeZero)
    val leftIsZero = mkFpIsZeroExpr(left)
    val rightIsZero = mkFpIsZeroExpr(right)
    val bothZero = mkAnd(leftIsZero, rightIsZero)
    val equalResult = mkIte(bothZero, signedZero, left)
    val rightGreaterResult = mkIte(mkFpGreaterExpr(right, left), right, equalResult)
    val leftGreaterResult = mkIte(mkFpGreaterExpr(left, right), left, rightGreaterResult)
    val rightNaNResult = mkIte(mkFpIsNaNExpr(right), right, leftGreaterResult)

    mkIte(mkFpIsNaNExpr(left), left, rightNaNResult)
}

private fun TsState.mathRound(value: UExpr<KFp64Sort>): UExpr<KFp64Sort> = with(ctx) {
    val roundingMode = fpRoundingModeSortDefaultValue()
    val floor = mkFpRoundToIntegralExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardNegative),
        value = value,
    )
    val fraction = mkFpSubExpr(roundingMode, value, floor)
    val half = mkFp(ROUNDING_THRESHOLD, fp64Sort)
    val useFloor = mkFpLessExpr(fraction, half)
    val increment = mkFp(ROUNDING_INCREMENT, fp64Sort)
    val incrementedFloor = mkFpAddExpr(roundingMode, floor, increment)
    val rounded = mkIte(useFloor, floor, incrementedFloor)
    val isNegative = mkFpIsNegativeExpr(value)
    val roundedIsZero = mkFpIsZeroExpr(rounded)
    val returnsNegativeZero = mkAnd(isNegative, roundedIsZero)
    val negativeZero = mkFp(NEGATIVE_ZERO, fp64Sort)
    val signedRounded = mkIte(returnsNegativeZero, negativeZero, rounded)
    val preserveInput = mkOr(
        mkFpIsNaNExpr(value),
        mkFpIsInfiniteExpr(value),
        mkFpIsZeroExpr(value),
    )

    mkIte(preserveInput, value, signedRounded)
}

private const val MAX_SAFE_INTEGER: Double = 9_007_199_254_740_991.0
private const val NEGATIVE_ZERO: Double = -0.0
private const val ROUNDING_THRESHOLD: Double = 0.5
private const val ROUNDING_INCREMENT: Double = 1.0
