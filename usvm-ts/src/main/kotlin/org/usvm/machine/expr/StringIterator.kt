package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.isTrue
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.util.allocateBoundedString
import org.usvm.util.arrayStorageType
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringLength

/** A string iterator keeps its original immutable string when the source binding is reassigned. */
data class TsStringIterator(
    val string: UHeapRef,
    val index: UExpr<TsSizeSort>,
    val exhausted: UBoolExpr,
)

private val stringIteratorType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%BuiltinStringIterator"))

internal fun TsExprResolver.tryCreateStringIterator(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    if (stmt.instance.sort != addressSort) return TsExprApproximationResult.NoApproximation

    val string = stmt.instance.asExpr(addressSort)
    val lengthBound = scope.calcOnState { boundedStringBackingRefs[string] }
    if (lengthBound == null) {
        val storageType = scope.calcOnState { arrayStorageType(string, stmt.call.instance.type) }
        val requiresStringBacking = storageType == EtsStringType || storageType is EtsStringLiteralType
        if (!requiresStringBacking) return TsExprApproximationResult.NoApproximation

        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.STRING_ITERATOR_UNSUPPORTED,
            detail = "String iteration requires modeled UTF-16 backing",
        )
        scope.assert(falseExpr)
        return TsExprApproximationResult.ResolveFailure
    }

    // The private backing registry is runtime evidence; a String annotation alone cannot create an iterator.
    val length = scope.calcOnState { stringLength(string) }
    ensureLengthBounds(scope = scope, length = length, maxLength = lengthBound)
        ?: return TsExprApproximationResult.ResolveFailure

    val iterator = scope.calcOnState {
        val reference = memory.allocConcrete(stringIteratorType)
        stringIterators += reference to TsStringIterator(string = string, index = mkBv(0), exhausted = falseExpr)
        reference
    }
    TsExprApproximationResult.SuccessfulApproximation(iterator)
}

internal fun TsExprResolver.advanceStringIterator(
    reference: UConcreteHeapRef,
    iterator: TsStringIterator,
): UHeapRef = with(ctx) {
    val length = scope.calcOnState { stringLength(iterator.string) }
    val done = mkOr(iterator.exhausted, mkBvSignedGreaterOrEqualExpr(iterator.index, length))
    val width: UExpr<TsSizeSort>
    val value: UHeapRef
    if (done.isTrue) {
        width = mkBv(0)
        value = mkUndefinedValue()
    } else {
        val nextIndex = mkBvAddExpr(iterator.index, mkBv(1))
        val (first, second) = scope.calcOnState {
            val characters = stringCharacters(iterator.string)
            memory.read(mkStringBackingElementLValue(characters, iterator.index)) to
                memory.read(mkStringBackingElementLValue(characters, nextIndex))
        }
        val firstIsHighSurrogate = mkEq(mkBvAndExpr(first, mkBv(0xfc00, bv16Sort)), mkBv(0xd800, bv16Sort))
        val secondIsLowSurrogate = mkEq(mkBvAndExpr(second, mkBv(0xfc00, bv16Sort)), mkBv(0xdc00, bv16Sort))
        val pair = mkAnd(firstIsHighSurrogate, mkBvSignedLessExpr(nextIndex, length), secondIsLowSurrogate)
        width = mkIte(pair, mkBv(2), mkBv(1))

        val character = scope.calcOnState {
            allocateBoundedString(length = width, codeUnits = listOf(first, second))
        }
        value = mkIte(done, mkUndefinedValue(), character)
    }

    scope.calcOnState {
        stringIterators += reference to iterator.copy(
            index = mkIte(done, iterator.index, mkBvAddExpr(iterator.index, width)),
            exhausted = done,
        )
        val result = memory.allocConcrete(iteratorResultType)
        iteratorResults += result to TsIteratorResult(done = done, value = value)
        result
    }
}
