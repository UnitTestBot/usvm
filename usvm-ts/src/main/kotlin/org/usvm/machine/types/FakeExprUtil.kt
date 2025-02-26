package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.IntermediateLValueField
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue
import org.usvm.types.single

fun TsContext.mkFakeValue(
    scope: TsStepScope,
    boolValue: UBoolExpr? = null,
    fpValue: UExpr<KFp64Sort>? = null,
    refValue: UHeapRef? = null,
): UConcreteHeapRef {
    require(boolValue != null || fpValue != null || refValue != null) {
        "Fake object should contain at least one value"
    }

    return scope.calcOnState {
        val fakeValueRef = createFakeObjectRef()
        val address = fakeValueRef.address

        val boolTypeExpr = trueExpr
            .takeIf { boolValue != null && fpValue == null && refValue == null }
            ?: makeSymbolicPrimitive(boolSort)
        val fpTypeExpr = trueExpr
            .takeIf { boolValue == null && fpValue != null && refValue == null }
            ?: makeSymbolicPrimitive(boolSort)
        val refTypeExpr = trueExpr
            .takeIf { boolValue == null && fpValue == null && refValue != null }
            ?: makeSymbolicPrimitive(boolSort)

        val type = FakeType(
            boolTypeExpr = boolTypeExpr,
            fpTypeExpr = fpTypeExpr,
            refTypeExpr = refTypeExpr,
        )
        memory.types.allocate(address, type)
        scope.assert(type.mkExactlyOneTypeConstraint(ctx))

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
}

fun <T : USort> TsState.extractValue(
    value: UExpr<out USort>,
    sort: T,
    extractIntermediateLValue: (Int) -> ULValue<*, T>,
): Pair<UExpr<T>?, UBoolExpr> = with(ctx) {
    when {
        value.isFakeObject() -> {
            val lValue = extractIntermediateLValue(value.address)

            val type = memory.typeStreamOf(value).single() as FakeType
            val typeCondition = when (sort) {
                boolSort -> type.boolTypeExpr
                fp64Sort -> type.fpTypeExpr
                addressSort -> type.refTypeExpr
                else -> error("Unsupported $sort")
            }
            memory.read(lValue) to typeCondition
        }

        value.sort == sort -> value.asExpr(sort) to trueExpr

        else -> null to falseExpr
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

    val fakeType = FakeType(
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
