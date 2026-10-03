package org.usvm.machine.state

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.typeStreamOf
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.solver.UUnsatResult
import org.usvm.types.singleOrNull
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkStringBackingLengthLValue

/** Recording a possible string adds no constraints or backing fields. */
internal fun TsState.trackSymbolicStringCandidate(ref: UHeapRef) {
    when (ref) {
        is UNullRef -> Unit
        is USymbolicHeapRef -> symbolicStringCandidates += ref
        is UIteExpr<*> -> {
            trackSymbolicStringCandidate(ref.trueBranch.asExpr(ctx.addressSort))
            trackSymbolicStringCandidate(ref.falseBranch.asExpr(ctx.addressSort))
        }
    }
}

/** The same input reference and field are used for typed inputs and later type refinements. */
internal fun TsState.symbolicStringBackingConstraint(ref: UHeapRef): UBoolExpr = with(ctx) {
    val charsRef = memory.read(mkFieldLValue(addressSort, ref, field = "value"))
    val charsType = EtsArrayType(EtsNumberType, dimensions = 1)
    val length = memory.read(mkStringBackingLengthLValue(charsRef))

    val definedBacking = mkNot(mkHeapRefEq(charsRef, mkUndefinedValue()))
    val backingType = memory.types.evalTypeEquals(charsRef, charsType)
    val nonnegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
    val boundedLength = mkBvSignedLessOrEqualExpr(length, mkBv(maxStringLength))

    mkAnd(definedBacking, backingType, nonnegativeLength, boundedLength)
}

/** Prepare only references that are already non-nullish strings on this execution path. */
internal fun TsStepScope.prepareRefinedStringBackings(): Unit? {
    val candidates = calcOnState {
        symbolicStringCandidates.filter { ref ->
            ref !in boundedStringBackingRefs && memory.typeStreamOf(ref).singleOrNull() == EtsStringType
        }
    }
    for (ref in candidates) {
        // A singleton type stream alone does not exclude nullish references.
        // Prove the full condition on the path, independently of any one solver model.
        val definitelyString = calcOnState {
            val alternative = clone()
            val stringCondition = with(ctx) {
                val stringType = memory.types.evalTypeEquals(ref, EtsStringType)
                val nonNull = mkNot(mkHeapRefEq(ref, mkTsNullValue()))
                val defined = mkNot(mkHeapRefEq(ref, mkUndefinedValue()))

                mkAnd(stringType, nonNull, defined)
            }
            alternative.pathConstraints += ctx.mkNot(stringCondition)

            ctx.solver<EtsType>().check(alternative.pathConstraints) is UUnsatResult
        }
        if (!definitelyString) continue

        val backingConstraint = calcOnState { symbolicStringBackingConstraint(ref) }
        assert(backingConstraint) ?: return null
        doWithState {
            boundedStringBackingRefs += ref
            symbolicStringCandidates -= ref
        }
    }

    return Unit
}
