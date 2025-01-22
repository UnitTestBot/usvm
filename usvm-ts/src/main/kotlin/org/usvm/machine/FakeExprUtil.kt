package org.usvm.machine

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.state.TSState
import org.usvm.memory.ULValue
import org.usvm.types.single

fun <T : USort> TSState.extractValue(
    value: UExpr<out USort>,
    sort: T,
    extractIntermediateLValue: (Int) -> ULValue<*, T>,
): Pair<UExpr<T>?, UBoolExpr> = with(ctx) {
    when {
        value.isFakeObject() -> {
            value as UConcreteHeapRef
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

fun TSContext.iteWriteIntoFakeObject(
    scope: TSStepScope,
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
        ctx.mkFp64Sort(),
        ::getIntermediateFpLValue
    )
    val (fpRValueFalseBranch, fpValueFalseBranchCondition) = extractValue(
        falseBranchValue,
        ctx.mkFp64Sort(),
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
        ctx,
        fakeObject.address,
        boolTypeExpr = mkIte(condition, boolTrueBranchCondition, boolFalseBranchCondition),
        fpTypeExpr = mkIte(condition, fpValueTrueBranchCondition, fpValueFalseBranchCondition),
        refTypeExpr = mkIte(condition, refValueTrueBranchCondition, refValueFalseBranchCondition)
    )

    memory.types.allocate(fakeObject.address, fakeType)

    fakeObject
}

private fun <T : USort> TSState.writeValuesWithGuard(
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
            memory.write(lValue, trueBranchValue, guard = trueExpr)
        }

        falseBranchValue != null -> {
            memory.write(lValue, falseBranchValue, guard = trueExpr)
        }

        else -> error("Neither of the values is non-null value")
    }
}