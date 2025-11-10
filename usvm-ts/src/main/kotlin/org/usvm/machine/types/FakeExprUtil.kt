package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRef
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.IntermediateLValueField
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue

private val logger = KotlinLogging.logger {}

fun TsState.mkFakeValue(
    scope: TsStepScope,
    type: EtsType? = null,
): UConcreteHeapRef = mkFakeValue(
    scope = scope,
    boolValue = makeSymbolicPrimitive(ctx.boolSort),
    fpValue = makeSymbolicPrimitive(ctx.fp64Sort),
    refValue = if (type != null) makeSymbolicRef(scope, type) else makeSymbolicRefUntyped(),
)

fun TsState.mkFakeValue(
    scope: TsStepScope?, // pass `null` only in the initial state, where `scope` is not available!
    boolValue: UBoolExpr? = null,
    fpValue: UExpr<KFp64Sort>? = null,
    refValue: UHeapRef? = null,
): UConcreteHeapRef = with(ctx) {
    require(boolValue != null || fpValue != null || refValue != null) {
        "Fake object should contain at least one value"
    }

    val boolTypeExpr = trueExpr
        .takeIf { boolValue != null && fpValue == null && refValue == null }
        ?: makeSymbolicPrimitive(boolSort)
    val fpTypeExpr = trueExpr
        .takeIf { boolValue == null && fpValue != null && refValue == null }
        ?: makeSymbolicPrimitive(boolSort)
    val refTypeExpr = trueExpr
        .takeIf { boolValue == null && fpValue == null && refValue != null }
        ?: makeSymbolicPrimitive(boolSort)

    createFakeObject(
        scope = scope,
        boolTypeExpr = boolTypeExpr,
        fpTypeExpr = fpTypeExpr,
        refTypeExpr = refTypeExpr,
        boolValue = boolValue,
        fpValue = fpValue,
        refValue = refValue,
    )
}

/**
 * Maps values of a fake object using the provided lambda functions while preserving the type discriminants.
 *
 * @param scope The step scope
 * @param fakeObject The original fake object to map
 * @param mapBool The mapping function to apply to the bool value
 * @param mapFp The mapping function to apply to the fp value
 * @param mapRef The mapping function to apply to the ref value
 * @return A new fake object with mapped values but the same type discriminants as the original
 */
fun TsState.mapFake(
    scope: TsStepScope,
    fakeObject: UConcreteHeapRef,
    mapBool: (UBoolExpr) -> UBoolExpr?,
    mapFp: (UExpr<KFp64Sort>) -> UExpr<KFp64Sort>?,
    mapRef: (UHeapRef) -> UHeapRef?,
): UConcreteHeapRef = with(ctx) {
    require(fakeObject.isFakeObject()) {
        "mapFake requires a fake object, but got: $fakeObject"
    }

    val refValue = fakeObject.extractRef(memory).let(mapRef)
    // If the mapped ref is already a fake object, return it directly without wrapping
    if (refValue != null && refValue.isFakeObject()) {
        // if (boolValue != null) {
        //     val boolValue2 = refValue.extractBool(memory)
        //     if (boolValue !== boolValue2 ) {
        //         error("Cannot map fake object to another fake object with bool value")
        //     }
        // }
        // if (fpValue != null) {
        //     val fpValue2 = refValue.extractFp(memory)
        //     if (fpValue !== fpValue2 ) {
        //         error("Cannot map fake object to another fake object with fp value")
        //     }
        // }
        // check(boolValue == null && fpValue == null) {
        //     "Cannot have bool/fp values when mapped ref is already a fake object: ref=$refValue, bool=$boolValue, fp=$fpValue"
        // }
        return refValue
    }

    val boolValue = fakeObject.extractBool(memory).let(mapBool)
    val fpValue = fakeObject.extractFp(memory).let(mapFp)

    val originalType = fakeObject.getFakeType(memory)

    createFakeObject(
        scope = scope,
        boolTypeExpr = originalType.boolTypeExpr,
        fpTypeExpr = originalType.fpTypeExpr,
        refTypeExpr = originalType.refTypeExpr,
        boolValue = boolValue,
        fpValue = fpValue,
        refValue = refValue,
    )
}

/**
 * Helper function to create a fake object with given type discriminants and values.
 */
private fun TsState.createFakeObject(
    scope: TsStepScope?,
    boolTypeExpr: UBoolExpr,
    fpTypeExpr: UBoolExpr,
    refTypeExpr: UBoolExpr,
    boolValue: UBoolExpr?,
    fpValue: UExpr<KFp64Sort>?,
    refValue: UHeapRef?,
): UConcreteHeapRef = with(ctx) {
    if (refValue != null) {
        check(!refValue.isFakeObject()) {
            "Nested fake objects are not supported: $refValue"
        }
    }

    val fakeValueRef = createFakeObjectRef()
    val address = fakeValueRef.address

    val type = EtsFakeType(
        boolTypeExpr = boolTypeExpr,
        fpTypeExpr = fpTypeExpr,
        refTypeExpr = refTypeExpr,
    )
    memory.types.allocate(address, type)
    val constraint = type.mkExactlyOneTypeConstraint(ctx)
    if (scope != null) {
        scope.assert(constraint) ?: run {
            logger.warn {
                "UNSAT after ensuring ExactlyOne type constraint for fake object: $fakeValueRef"
            }
        }
    } else {
        pathConstraints += constraint
    }

    if (boolValue != null) {
        val boolLValue = ctx.getIntermediateBoolLValue(address)
        memory.write(boolLValue, boolValue, guard = ctx.trueExpr)
    }

    if (fpValue != null) {
        val fpLValue = ctx.getIntermediateFpLValue(address)
        memory.write(fpLValue, fpValue, guard = ctx.trueExpr)
    }

    if (refValue != null) {
        val refLValue = ctx.getIntermediateRefLValue(address)
        memory.write(refLValue, refValue, guard = ctx.trueExpr)
    }

    fakeValueRef
}

fun <T : USort> TsState.extractValue(
    value: UExpr<out USort>,
    sort: T,
    extractIntermediateLValue: (Int) -> ULValue<*, T>,
): Pair<UExpr<T>?, UBoolExpr> = with(ctx) {
    when {
        value.isFakeObject() -> {
            val lValue = extractIntermediateLValue(value.address)
            val type = value.getFakeType(memory)
            val typeCondition = when (sort) {
                boolSort -> type.boolTypeExpr
                fp64Sort -> type.fpTypeExpr
                addressSort -> type.refTypeExpr
                else -> error("Unsupported $sort")
            }
            memory.read(lValue) to typeCondition
        }

        value.sort == sort -> {
            value.asExpr(sort) to trueExpr
        }

        else -> {
            null to falseExpr
        }
    }
}

fun TsContext.iteWriteIntoFakeObject(
    scope: TsStepScope,
    condition: UBoolExpr,
    trueBranchValue: UExpr<out USort>,
    falseBranchValue: UExpr<out USort>,
): UConcreteHeapRef = scope.calcOnState {
    val (boolRValueTrueBranch, boolTrueBranchCondition) = extractValue(
        trueBranchValue,
        ctx.boolSort,
        ::getIntermediateBoolLValue
    )
    val (boolRValueFalseBranch, boolFalseBranchCondition) = extractValue(
        falseBranchValue,
        ctx.boolSort,
        ::getIntermediateBoolLValue
    )

    val (fpRValueTrueBranch, fpValueTrueBranchCondition) = extractValue(
        trueBranchValue,
        ctx.fp64Sort,
        ::getIntermediateFpLValue
    )
    val (fpRValueFalseBranch, fpValueFalseBranchCondition) = extractValue(
        falseBranchValue,
        ctx.fp64Sort,
        ::getIntermediateFpLValue
    )

    val (refRValueTrueBranch, refValueTrueBranchCondition) = extractValue(
        trueBranchValue,
        ctx.addressSort,
        ::getIntermediateRefLValue
    )
    val (refRValueFalseBranch, refValueFalseBranchCondition) = extractValue(
        falseBranchValue,
        ctx.addressSort,
        ::getIntermediateRefLValue
    )

    val fakeObject = createFakeObjectRef()

    val boolLValue = getIntermediateBoolLValue(fakeObject.address)
    val fpLValue = getIntermediateFpLValue(fakeObject.address)
    val refLValue = getIntermediateRefLValue(fakeObject.address)

    writeValuesWithGuard(boolRValueTrueBranch, boolRValueFalseBranch, boolLValue, condition)
    writeValuesWithGuard(fpRValueTrueBranch, fpRValueFalseBranch, fpLValue, condition)
    writeValuesWithGuard(refRValueTrueBranch, refRValueFalseBranch, refLValue, condition)

    val fakeType = EtsFakeType(
        boolTypeExpr = mkIte(condition, boolTrueBranchCondition, boolFalseBranchCondition),
        fpTypeExpr = mkIte(condition, fpValueTrueBranchCondition, fpValueFalseBranchCondition),
        refTypeExpr = mkIte(condition, refValueTrueBranchCondition, refValueFalseBranchCondition)
    ).also {
        scope.assert(it.mkExactlyOneTypeConstraint(ctx))
    }

    memory.types.allocate(fakeObject.address, fakeType)

    fakeObject
}

private fun <T : USort> TsState.writeValuesWithGuard(
    trueBranchValue: UExpr<T>?,
    falseBranchValue: UExpr<T>?,
    lValue: UFieldLValue<IntermediateLValueField, T>,
    condition: UBoolExpr,
) = with(ctx) {
    when {
        trueBranchValue != null && falseBranchValue != null -> {
            memory.write(lValue, trueBranchValue, condition)
            memory.write(lValue, falseBranchValue, condition.not())
        }

        trueBranchValue != null -> {
            // We can omit using the condition here, because if the condition is false,
            // there will be no reading from this value.
            memory.write(lValue, trueBranchValue, guard = trueExpr)
        }

        falseBranchValue != null -> {
            memory.write(lValue, falseBranchValue, guard = trueExpr)
        }

        else -> {
            // Neither of the values is non-null value
        }
    }
}
