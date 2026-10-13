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

private const val SURROGATE_KIND_MASK = 0xfc00
private const val HIGH_SURROGATE_KIND = 0xd800
private const val LOW_SURROGATE_KIND = 0xdc00

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
): UHeapRef? = with(ctx) {
    val length = scope.calcOnState { stringLength(iterator.string) }
    val done = mkOr(iterator.exhausted, mkBvSignedGreaterOrEqualExpr(iterator.index, length))
    val isDone = scope.calcOnState { models.first().eval(done).isTrue }
    val exhaustionBranch = if (isDone) done else mkNot(done)
    scope.fork(exhaustionBranch) ?: return null

    val width: UExpr<TsSizeSort>
    val value: UHeapRef
    if (isDone) {
        width = mkBv(0)
        value = mkUndefinedValue()
    } else {
        val nextIndex = mkBvAddExpr(iterator.index, mkBv(1))
        val (first, second) = scope.calcOnState {
            val characters = stringCharacters(iterator.string)
            memory.read(mkStringBackingElementLValue(characters, iterator.index)) to
                memory.read(mkStringBackingElementLValue(characters, nextIndex))
        }
        val surrogateKindMask = mkBv(SURROGATE_KIND_MASK, bv16Sort)
        val firstKind = mkBvAndExpr(first, surrogateKindMask)
        val secondKind = mkBvAndExpr(second, surrogateKindMask)
        val firstIsHighSurrogate = mkEq(firstKind, mkBv(HIGH_SURROGATE_KIND, bv16Sort))
        val secondIsLowSurrogate = mkEq(secondKind, mkBv(LOW_SURROGATE_KIND, bv16Sort))
        val pair = mkAnd(firstIsHighSurrogate, mkBvSignedLessExpr(nextIndex, length), secondIsLowSurrogate)
        val isPair = scope.calcOnState { models.first().eval(pair).isTrue }
        val widthBranch = if (isPair) pair else mkNot(pair)
        scope.fork(widthBranch) ?: return null

        width = if (isPair) mkBv(2) else mkBv(1)

        value = scope.calcOnState {
            allocateBoundedString(length = width, codeUnits = listOf(first, second))
        }
    }

    scope.calcOnState {
        stringIterators += reference to iterator.copy(
            index = mkBvAddExpr(iterator.index, width),
            exhausted = mkBool(isDone),
        )
        val result = memory.allocConcrete(iteratorResultType)
        iteratorResults += result to TsIteratorResult(done = mkBool(isDone), value = value)
        result
    }
}
