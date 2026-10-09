package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.UOrExpr
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.api.allocateConcreteRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.types.single
import org.usvm.types.singleOrNull
import org.usvm.util.boolToFp
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

fun TsContext.checkNotFake(expr: UExpr<*>) {
    require(!expr.isFakeObject()) {
        "Fake object handling should be done outside of this function"
    }
}

// `any` is assignable both to and from string, so a type-relation query cannot identify
// a materialized string. Inspect the concrete type stream before reading its backing array.
private fun TsState.stringTypeCondition(ref: UHeapRef): UBoolExpr = with(ctx) {
    if (ref is UNullRef) return@with falseExpr

    when (ref) {
        is UConcreteHeapRef, is USymbolicHeapRef -> {
            val type = memory.types.getTypeStream(ref).singleOrNull()
            if (type is EtsStringType || type is EtsStringLiteralType) {
                mkTrue()
            } else {
                memory.types.evalTypeEquals(ref, EtsStringType)
            }
        }

        is UIteExpr<*> -> {
            val trueRef = ref.trueBranch.asExpr(addressSort)
            val falseRef = ref.falseBranch.asExpr(addressSort)
            val trueIsString = stringTypeCondition(trueRef)
            val falseIsString = stringTypeCondition(falseRef)

            mkIte(ref.condition, trueIsString, falseIsString)
        }

        else -> mkFalse()
    }
}

private fun TsState.hasStringBacking(ref: UHeapRef): UBoolExpr = with(ctx) {
    when (ref) {
        is UConcreteHeapRef -> {
            if (getStringConstantValue(ref) != null || ref in boundedStringBackingRefs) mkTrue() else mkFalse()
        }

        is USymbolicHeapRef -> {
            if (ref in boundedStringBackingRefs) mkTrue() else mkFalse()
        }

        is UIteExpr<*> -> {
            val trueRef = ref.trueBranch.asExpr(addressSort)
            val falseRef = ref.falseBranch.asExpr(addressSort)

            mkIte(ref.condition, hasStringBacking(trueRef), hasStringBacking(falseRef))
        }

        else -> mkFalse()
    }
}

private fun TsState.referenceTruthy(ref: UHeapRef): UBoolExpr = with(ctx) {
    val nonNullish = mkNotNullOrUndefined(ref)
    if (nonNullish.isFalse) return@with mkFalse()

    val isString = stringTypeCondition(ref)
    if (isString.isFalse) return@with nonNullish

    val backedString = hasStringBacking(ref)
    if (backedString.isFalse) return@with nonNullish

    val charsRef = memory.read(mkStringBackingLValue(ref))
    val readableBacking = mkAnd(isString, backedString)
    val readableCharsRef = if (readableBacking.isTrue) {
        charsRef
    } else {
        // Other objects need no backing array. Keep the array-region read away from their null field value.
        mkIte(readableBacking, charsRef, allocateConcreteRef())
    }
    val length = memory.read(mkStringBackingLengthLValue(readableCharsRef))
    val stringIsNonEmpty = mkNot(mkEq(length, mkBv(0)))

    mkAnd(nonNullish, mkImplies(isString, stringIsNonEmpty))
}

/** Validate the execution path separately from constructing its boolean expression. */
fun TsStepScope.ensureTruthinessSupported(expr: UExpr<out USort>): Unit? {
    val unsupportedString = calcOnState {
        with(ctx) {
            val (ref, activeGuard) = when {
                expr.isFakeObject() -> {
                    val type = expr.getFakeType(memory)
                    memory.read(getIntermediateRefLValue(expr.address)) to type.refTypeExpr
                }

                expr.sort == addressSort -> expr.asExpr(addressSort) to trueExpr
                else -> return@calcOnState falseExpr
            }

            mkAnd(activeGuard, mkNotNullOrUndefined(ref), stringTypeCondition(ref), mkNot(hasStringBacking(ref)))
        }
    }
    if (unsupportedString.isFalse) return Unit

    val supported = calcOnState { ctx.mkNot(unsupportedString) }
    return fork(supported, blockOnFalseState = {
        terminateAsUnsupported(reason = "Truthiness needs a modeled string backing for dynamic references")
    })
}

/** Construct a condition; call [ensureTruthinessSupported] before executing with it. */
fun TsContext.mkTruthyExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UBoolExpr = scope.calcOnState {
    if (expr.isFakeObject()) {
        val falseBranchGround = makeSymbolicPrimitive(boolSort)

        val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
        val possibleType = memory.types.getTypeStream(expr.asExpr(addressSort)).single() as EtsFakeType

        scope.doWithState {
            pathConstraints += possibleType.mkExactlyOneTypeConstraint(this@mkTruthyExpr)
        }

        if (!possibleType.boolTypeExpr.isFalse) {
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.boolTypeExpr,
                expr = memory.read(getIntermediateBoolLValue(expr.address))
            )
        }

        if (!possibleType.fpTypeExpr.isFalse) {
            val value = memory.read(getIntermediateFpLValue(expr.address))
            val numberCondition = mkAnd(
                mkFpEqualExpr(value.asExpr(fp64Sort), mkFp(0.0, fp64Sort)).not(),
                mkFpIsNaNExpr(value.asExpr(fp64Sort)).not()
            )
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.fpTypeExpr,
                expr = numberCondition
            )
        }

        if (!possibleType.refTypeExpr.isFalse) {
            val value = memory.read(getIntermediateRefLValue(expr.address))
            val refTruthy = referenceTruthy(value)
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.refTypeExpr,
                expr = refTruthy
            )
        }

        conjuncts.foldRight(falseBranchGround) { (condition, value), acc ->
            mkIte(condition, value, acc)
        }
    } else {
        // ECMAScript ToBoolean (https://tc39.es/ecma262/#sec-toboolean).

        when (expr.sort) {
            boolSort -> expr.asExpr(boolSort)

            fp64Sort -> mkAnd(
                mkFpEqualExpr(expr.asExpr(fp64Sort), mkFp(0.0, fp64Sort)).not(),
                mkFpIsNaNExpr(expr.asExpr(fp64Sort)).not()
            )

            addressSort -> referenceTruthy(expr.asExpr(addressSort))

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }
}

fun TsContext.mkNumericExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UExpr<KFp64Sort> {
    if (expr.isFakeObject()) {
        val type = expr.getFakeType(scope)
        return mkIte(
            condition = type.fpTypeExpr,
            trueBranch = expr.extractFp(scope),
            falseBranch = mkIte(
                condition = type.boolTypeExpr,
                trueBranch = mkNumericExpr(expr.extractBool(scope), scope),
                falseBranch = mkNumericExpr(expr.extractRef(scope), scope)
            )
        )
    }

    // 7.1.4 ToNumber ( argument )
    //
    // 1. If argument is a Number, return argument.
    // 2. If argument is either a Symbol or a BigInt, throw a TypeError exception.
    // 3. If argument is undefined, return NaN.
    // 4. If argument is either null or false, return +0𝔽.
    // 5. If argument is true, return 1𝔽.
    // 6. If argument is a String, return StringToNumber(argument).
    // 7. Assert: argument is an Object.
    // 8. Let primValue be ToPrimitive(argument, "number").
    // 9. Assert: primValue is not an Object.
    // 10. Return ToNumber(primValue).

    if (expr.sort == fp64Sort) {
        return expr.asExpr(fp64Sort)
    }

    if (expr == mkUndefinedValue()) {
        return mkFp64NaN()
    }

    if (expr == mkTsNullValue()) {
        return mkFp64(0.0)
    }

    if (expr.sort == boolSort) {
        return boolToFp(expr.asExpr(boolSort))
    }

    // TODO: ToPrimitive, then ToNumber again
    // TODO: probably we need to implement Object (Ref/Fake) -> Number conversion here directly, without ToPrimitive

    // TODO incorrect implementation, returns some number that is not equal to 0 and NaN
    //      https://github.com/UnitTestBot/usvm/issues/280
    return mkIte(
        condition = mkEq(expr.asExpr(addressSort), mkTsNullValue()),
        trueBranch = mkFp(0.0, fp64Sort),
        falseBranch = mkIte(
            mkEq(expr.asExpr(addressSort), mkUndefinedValue()),
            mkFp64NaN(),
            mkFp64NaN()
        )
    )
}

fun TsContext.mkNullishExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UBoolExpr {
    // Handle fake objects specially
    if (expr.isFakeObject()) {
        val fakeType = expr.getFakeType(scope)
        val ref = expr.extractRef(scope)
        // Only check for nullish if the fake object represents a reference type.
        // If it represents a primitive type (bool/number), it's never nullish.
        return mkIte(
            condition = fakeType.refTypeExpr,
            trueBranch = mkIsNullOrUndefined(ref),
            falseBranch = mkFalse(),
        )
    }

    // Regular reference is nullish if it is either null or undefined
    if (expr.sort == addressSort) {
        val ref = expr.asExpr(addressSort)
        return mkIsNullOrUndefined(ref)
    }

    // Non-reference types (numbers, booleans, strings) are never nullish
    return mkFalse()
}

fun TsState.throwException(reason: String) {
    val ref = ctx.mkStringConstantRef(reason)
    methodResult = TsMethodResult.TsException(ref, EtsStringType)
}

fun TsContext.mkIsNullOrUndefined(ref: UHeapRef): UBoolExpr {
    checkNotFake(ref)

    val isNull = mkHeapRefEq(ref, mkTsNullValue())
    val isUndefined = mkHeapRefEq(ref, mkUndefinedValue())
    return mkOr(isNull, isUndefined)
}

fun TsContext.mkNotNullOrUndefined(ref: UHeapRef): UBoolExpr {
    val isNullOrUndefined = mkIsNullOrUndefined(ref)

    // Preserve the explicit disequalities when this expression is composed into path guards.
    return when (isNullOrUndefined) {
        is UOrExpr -> mkAnd(isNullOrUndefined.args.map(::mkNot))
        else -> mkNot(isNullOrUndefined)
    }
}

fun TsContext.checkUndefinedOrNullPropertyRead(
    scope: TsStepScope,
    instance: UHeapRef,
    propertyName: String,
): Unit? {
    require(!instance.isFakeObject()) {
        "Fake object handling should be done outside of this function"
    }
    val condition = mkNotNullOrUndefined(instance)
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Undefined or null property access: $propertyName of $instance") }
    )
}

fun TsContext.checkNegativeIndexRead(
    scope: TsStepScope,
    index: UExpr<TsSizeSort>,
): Unit? {
    val condition = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Negative index access: $index") }
    )
}

fun TsContext.checkReadingInRange(
    scope: TsStepScope,
    index: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
): Unit? {
    val condition = mkBvSignedLessExpr(index, length)
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Index out of bounds: $index, length: $length") }
    )
}

fun TsContext.ensureLengthBounds(
    scope: TsStepScope,
    length: UExpr<TsSizeSort>,
    maxLength: Int,
): Unit? {
    // Check that length is non-negative and does not exceed `maxLength`.
    val condition = mkAnd(
        mkBvSignedGreaterOrEqualExpr(length, mkBv(0)),
        mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
    )
    return scope.assert(condition)
}
