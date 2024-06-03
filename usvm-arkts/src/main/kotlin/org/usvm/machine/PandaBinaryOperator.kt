package org.usvm.machine

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaStringType
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading

sealed class PandaBinaryOperator(
    // TODO undefinedObject?
    val onBool: PandaContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onNumber: PandaContext.(UExpr<KFp64Sort>, UExpr<KFp64Sort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onString: PandaContext.(UExpr<USort>, UExpr<USort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
) {
    object Add : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
        },
        onNumber = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
        },
        onNumber = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
        },
        onNumber = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
        },
        onNumber = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Gt : PandaBinaryOperator(
        onBool = { lhs, rhs -> mkAnd(lhs, rhs.not()) }, // lhs > rhs => lhs /\ !rhs
        onNumber = PandaContext::mkFpGreaterExpr
    )

    object Lt : PandaBinaryOperator(
        onBool = { lhs, rhs -> mkAnd(lhs.not(), rhs) }, // lhs < rhs => !lhs /\ rhs
        onNumber = PandaContext::mkFpLessExpr
    )

    object Eq : PandaBinaryOperator(
        onBool = PandaContext::mkEq,
        onNumber = PandaContext::mkFpEqualExpr,
    )

    object Neq : PandaBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onNumber = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    private fun UExpr<out USort>.checkInterpreted(): Boolean {
        return this is KInterpretedValue || this is UInputArrayLengthReading<*, *> || this is UInputArrayReading<*, *, *>
    }

    internal open operator fun invoke(
        lhs: PandaUExprWrapper,
        rhs: PandaUExprWrapper,
        scope: PandaStepScope
    ): UExpr<out USort> {
        val ctx = lhs.uExpr.pctx
        val addressSort = ctx.addressSort

        var lhsUExpr = ctx.extractPrimitiveValueIfRequired(lhs.uExpr, scope)
        var rhsUExpr = ctx.extractPrimitiveValueIfRequired(rhs.uExpr, scope)
        if (lhsUExpr.sort == addressSort) {
            val newSort = ctx.typeToSort(lhs.from.type).takeIf { it != addressSort } ?: ctx.fp64Sort
            lhsUExpr = scope.calcOnState {
                memory.read(ctx.constructAuxiliaryFieldLValue(lhsUExpr.asExpr(addressSort), newSort))
            }
        }

        if (lhsUExpr.sort != rhsUExpr.sort) {
            rhsUExpr = rhs.withSortNew(ctx, lhsUExpr.sort)
        }

        val lhsSort = lhsUExpr.sort
        val rhsSort = rhsUExpr.sort

        return when {

            lhsSort is PandaBoolSort -> lhsUExpr.pctx.onBool(lhsUExpr.cast(), rhsUExpr.cast())

            lhsSort is PandaStringSort -> lhsUExpr.pctx.onString(lhsUExpr.cast(), rhsUExpr.cast())

            lhsSort is PandaNumberSort -> lhsUExpr.pctx.onNumber(lhsUExpr.cast(), rhsUExpr.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }

    }

    private fun PandaUExprWrapper.withSortNew(ctx: PandaContext, sort: USort): UExpr<out USort> = with(ctx) {
        when (sort) {
            fp64Sort -> when (uExpr.sort) {
                boolSort -> mkIte(uExpr.asExpr(boolSort), 1.0.toFp(), 0.0.toFp()).asExpr(fp64Sort)
                fp64Sort -> uExpr
                addressSort -> {
                    val newSort = ctx.typeToSort(from.type).takeIf { it != addressSort } ?: ctx.fp64Sort
                    this@withSortNew.withSortNew(ctx, newSort)
                }
                else -> error("Unprocessed sort pair: $sort to ${uExpr.sort}")
            }

            boolSort -> when (uExpr.sort) {
                fp64Sort -> mkIte(mkEq(uExpr.asExpr(fp64Sort), mkFp64(1.0)), mkTrue(), mkFalse()).asExpr(boolSort)
                boolSort -> uExpr
                else -> error("Unprocessed sort pair: $sort to ${uExpr.sort}")
            }

            addressSort -> this@withSortNew.withSortNew(ctx, this.typeToSort(from.type))
            else -> error("Unprocessed sort pair: $sort to ${uExpr.sort}")
        }
    }

    private fun makeAdditionalWorkRhs(
        lhs: UExpr<out USort>,
        rhs: UHeapRef,
        scope: PandaStepScope,
    ): UHeapRef = constructIteWithFixedLeftOperandType(lhs, rhs, scope)

    private fun constructIteWithFixedLeftOperandType(
        lhs: UExpr<out USort>,
        rhs: UHeapRef,
        scope: PandaStepScope,
    ): UHeapRef = with(lhs.ctx) {
        with(scope) {
            mkIte(
                condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaNumberType) },
                trueBranch = run {
                    val value = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, fp64Sort)) }
                    commonAdditionalWork(lhs, value, this)
                },
                falseBranch = mkIte(
                    condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaBoolType) },
                    trueBranch = run {
                        val value = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, boolSort)) }
                        commonAdditionalWork(lhs, value, this)
                    },
                    falseBranch = mkIte(
                        condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaStringType) },
                        trueBranch = run {
                            val value =
                                calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, ctx.stringSort)) }
                            commonAdditionalWork(lhs, value, this)
                        },
                        falseBranch = commonAdditionalWork(lhs, rhs, this)
                    )
                )
            )
        }
    }

    private fun makeAdditionalWorkLhs(
        lhs: UHeapRef,
        rhs: UExpr<out USort>,
        scope: PandaStepScope,
    ): UHeapRef = with(lhs.ctx) {
        with(scope) {
            mkIte(
                condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaNumberType) },
                trueBranch = run {
                    val value = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, fp64Sort)) }
                    commonAdditionalWork(value, rhs, this)
                },
                falseBranch = mkIte(
                    condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaBoolType) },
                    trueBranch = run {
                        val value = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, boolSort)) }
                        commonAdditionalWork(value, rhs, this)
                    },
                    falseBranch = mkIte(
                        condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaStringType) },
                        trueBranch = run {
                            val value = calcOnState {
                                memory.read(ctx.constructAuxiliaryFieldLValue(lhs, ctx.stringSort))
                            }
                            commonAdditionalWork(value, rhs, this)
                        },
                        falseBranch = commonAdditionalWork(lhs, rhs, scope)
                    )
                )
            )
        }
    }

    private fun makeAdditionalWork(
        lhs: UHeapRef,
        rhs: UHeapRef,
        scope: PandaStepScope,
    ): UHeapRef = with(lhs.ctx) {
        with(scope) {
            mkIte(
                condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaNumberType) },
                trueBranch = run {
                    val lhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, fp64Sort)) }
                    constructIteWithFixedLeftOperandType(lhsValue, rhs, scope)
                },
                falseBranch = mkIte(
                    condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaBoolType) },
                    trueBranch = run {
                        val lhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, boolSort)) }
                        constructIteWithFixedLeftOperandType(lhsValue, rhs, scope)
                    },
                    falseBranch = mkIte(
                        condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaStringType) },
                        trueBranch = run {
                            val lhsValue = calcOnState {
                                memory.read(ctx.constructAuxiliaryFieldLValue(lhs, ctx.stringSort))
                            }
                            constructIteWithFixedLeftOperandType(lhsValue, rhs, scope)
                        },
                        falseBranch = constructIteWithFixedLeftOperandType(lhs, rhs, scope)
                    )
                )
            )
        }
    }

    private fun commonAdditionalWork(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: PandaStepScope,
    ): UHeapRef = returnNanIfRequired(lhs, rhs, scope) {
        with(lhs.pctx) {
            when (lhs.sort) {
                fp64Sort -> when (rhs.sort) {
                    fp64Sort -> numberToNumber(lhs.cast(), rhs.cast(), scope)
                    boolSort -> numberToBool(lhs.cast(), rhs.cast(), scope)
                    stringSort -> numberToString(lhs.cast(), rhs.cast(), scope)
                    else -> numberToObject(lhs.cast(), rhs.cast(), scope)
                }

                boolSort -> when (rhs.sort) {
                    fp64Sort -> boolToNumber(lhs.cast(), rhs.cast(), scope)
                    boolSort -> boolToBool(lhs.cast(), rhs.cast(), scope)
                    stringSort -> boolToString(lhs.cast(), rhs.cast(), scope)
                    else -> boolToObject(lhs.cast(), rhs.cast(), scope)
                }

                stringSort -> when (rhs.sort) {
                    fp64Sort -> stringToNumber(lhs.cast(), rhs.cast(), scope)
                    boolSort -> stringToBool(lhs.cast(), rhs.cast(), scope)
                    stringSort -> stringToString(lhs.cast(), rhs.cast(), scope)
                    else -> stringToObject(lhs.cast(), rhs.cast(), scope)
                }

                else -> when (rhs.sort) {
                    fp64Sort -> objectToNumber(lhs.cast(), rhs.cast(), scope)
                    boolSort -> objectToBool(lhs.cast(), rhs.cast(), scope)
                    stringSort -> objectToString(lhs.cast(), rhs.cast(), scope)
                    else -> objectToObject(lhs.cast(), rhs.cast(), scope)
                }
            }
        }
    }


    private fun numberToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        val value = with(lhs.pctx) { onNumber(lhs, rhs) }
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaNumberType) }
        scope.doWithState { memory.write(lhs.pctx.constructAuxiliaryFieldLValue(newAddr, ctx.fp64Sort), value) }
        return newAddr
    }

    private fun numberToBool(lhs: UExpr<KFp64Sort>, rhs: UExpr<KBoolSort>, scope: PandaStepScope): UConcreteHeapRef {
        val rhsValue = with(lhs.pctx) { mkIte(rhs, 1.0.toFp(), 0.0.toFp()).asExpr(fp64Sort) }
        val value = with(lhs.pctx) { onNumber(lhs, rhsValue) }
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaNumberType) }
        scope.doWithState { memory.write(lhs.pctx.constructAuxiliaryFieldLValue(newAddr, ctx.fp64Sort), value) }
        return newAddr
    }

    private fun numberToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    // TODO ignore args completely????
    private fun numberToObject(
        lhs: UExpr<KFp64Sort>,
        rhs: UExpr<UAddressSort>,
        scope: PandaStepScope,
    ): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun boolToNumber(lhs: UExpr<KBoolSort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        val rhsValue = with(lhs.pctx) { mkIte(mkEq(rhs, mkFp64(1.0)), mkTrue(), mkFalse()).asExpr(boolSort) }
        val value = with(lhs.pctx) { onBool(lhs, rhsValue) }
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaBoolType) }
        scope.doWithState { memory.write(lhs.pctx.constructAuxiliaryFieldLValue(newAddr, ctx.boolSort), value) }
        return newAddr
    }

    private fun boolToBool(lhs: UExpr<KBoolSort>, rhs: UExpr<KBoolSort>, scope: PandaStepScope): UConcreteHeapRef {
        val value = with(lhs.pctx) { onBool(lhs, rhs) }
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaNumberType) }
        scope.doWithState { memory.write(lhs.pctx.constructAuxiliaryFieldLValue(newAddr, ctx.boolSort), value) }
        return newAddr
    }

    private fun boolToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun boolToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun stringToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun stringToBool(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun stringToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun stringToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun objectToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun objectToBool(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun objectToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun objectToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        return createFakeString(scope)
    }

    private fun createFakeString(scope: PandaStepScope): UConcreteHeapRef {
        val value = scope.calcOnState { memory.mocker.createMockSymbol(trackedLiteral = null, ctx.stringSort) }
        val address = scope.calcOnState { memory.allocConcrete(PandaStringType) }
        scope.doWithState { memory.write(ctx.constructAuxiliaryFieldLValue(address, ctx.stringSort), value) }
        return address
    }

    private fun returnNanIfRequired(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: PandaStepScope,
        applyOperator: () -> UConcreteHeapRef,
    ): UConcreteHeapRef = with(lhs.pctx) {
        if (this@PandaBinaryOperator is Add) {
            return applyOperator()
        }

        if ((lhs.sort == fp64Sort || lhs.sort == boolSort) && (rhs.sort == fp64Sort || rhs.sort == boolSort)) {
            return applyOperator()
        }

        val value = lhs.ctx.mkFp64NaN()
        val address = scope.calcOnState { memory.allocConcrete(PandaNumberType) }
        val lValue = lhs.pctx.constructAuxiliaryFieldLValue(address, fp64Sort)
        scope.doWithState { memory.write(lValue, value) }

        return address
    }
}
