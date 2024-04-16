package org.usvm.machine

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaField
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaObjectType
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.types.UTypeStream
import org.usvm.types.single

sealed class PandaBinaryOperator(
    // TODO undefinedObject?
    val onBool: PandaContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onNumber: PandaContext.(UExpr<KFp64Sort>, UExpr<KFp64Sort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onString: PandaContext.(UExpr<USort>, UExpr<USort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
) {
    object Add : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            with(lhs.ctx) {
                mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
            }
        },
        onNumber = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Gt : PandaBinaryOperator(
        onNumber = PandaContext::mkFpGreaterExpr
    )

    object Eq : PandaBinaryOperator(
        onBool = PandaContext::mkEq,
        onNumber = PandaContext::mkFpEqualExpr,
    )

    object Neq : PandaBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onNumber = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    internal open operator fun invoke(
        lhs: PandaUExprWrapper,
        rhs: PandaUExprWrapper,
        typeExtractor: (UConcreteHeapRef) -> UTypeStream<PandaType>,
        fieldReader: (UFieldLValue<PandaField, USort>) -> UExpr<out USort>,
        scope: PandaStepScope,
    ): UExpr<out USort> {
        var lhsUExpr = lhs.uExpr
        var rhsUExpr = rhs.uExpr

        val ctx = lhsUExpr.pctx

        if (lhsUExpr is UConcreteHeapRef) {
            val type = typeExtractor(lhsUExpr).single()
            lhsUExpr = when (type) {
                PandaNumberType -> fieldReader(ctx.constructAuxiliaryFieldLValue(lhsUExpr, ctx.fp64Sort))
                PandaBoolType -> fieldReader(ctx.constructAuxiliaryFieldLValue(lhsUExpr, ctx.boolSort))
                PandaStringType -> TODO()
                else -> lhsUExpr
            }
        }

        if (rhsUExpr is UConcreteHeapRef) {
            val type = typeExtractor(rhsUExpr).single()
            rhsUExpr = when (type) {
                PandaNumberType -> fieldReader(ctx.constructAuxiliaryFieldLValue(rhsUExpr, ctx.fp64Sort))
                PandaBoolType -> fieldReader(ctx.constructAuxiliaryFieldLValue(rhsUExpr, ctx.boolSort))
                PandaStringType -> TODO()
                else -> rhsUExpr
            }
        }

        val types = listOf(PandaNumberType, PandaStringType, PandaBoolType, PandaObjectType)

        if (lhsUExpr is KInterpretedValue && rhsUExpr is KInterpretedValue) {
            return commonAdditionalWork(lhsUExpr, rhsUExpr, scope)
        }

        val addressSort = ctx.addressSort
        if (lhsUExpr is KInterpretedValue) {
            require(rhsUExpr.sort === addressSort) { TODO() }
            return makeAdditionalWork(lhsUExpr, rhsUExpr.asExpr(addressSort), scope)
        }

        if (rhsUExpr is KInterpretedValue) {
            require(lhsUExpr.sort === addressSort) { TODO() }
            return makeAdditionalWork(lhsUExpr.asExpr(addressSort), rhsUExpr, scope)
        }

        require(lhsUExpr.sort === addressSort && rhsUExpr.sort === addressSort)

        return makeAdditionalWork(lhsUExpr.asExpr(addressSort), rhsUExpr.asExpr(addressSort), scope)
    }

    private fun makeAdditionalWork(
        lhs: KInterpretedValue<out USort>,
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
                            error("TODO")
                        },
                        falseBranch = rhs
                    )
                )
            )
        }
    }

    private fun makeAdditionalWork(
        lhs: UHeapRef,
        rhs: KInterpretedValue<out USort>,
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
                        trueBranch = run { error("TODO") },
                        falseBranch = lhs
                    )
                )
            )
        }
    }

    // TODO STRINGS
    private fun commonAdditionalWork(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: PandaStepScope,
    ): UHeapRef = with(lhs.pctx) {
        when (lhs.sort) {
            fp64Sort -> when (rhs.sort) {
                fp64Sort -> numberToNumber(lhs.cast(), rhs.cast(), scope)
                boolSort -> numberToBool(lhs.cast(), rhs.cast(), scope)
                /* string????? */
                else -> numberToObject(lhs.cast(), rhs.cast(), scope)
            }
            boolSort -> when (rhs.sort) {
                fp64Sort -> boolToNumber(lhs.cast(), rhs.cast(), scope)
                boolSort -> boolToBool(lhs.cast(), rhs.cast(), scope)
                /* string????? */
                else -> boolToObject(lhs.cast(), rhs.cast(), scope)
            }
            else -> when (rhs.sort) {
                fp64Sort -> objectToNumber(lhs.cast(), rhs.cast(), scope)
                boolSort -> objectToBool(lhs.cast(), rhs.cast(), scope)
                /* string????? */
                else -> objectToObject(lhs.cast(), rhs.cast(), scope)
            }
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
                    mkIte(
                        condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaNumberType) },
                        trueBranch = run {
                            val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, fp64Sort)) }
                            commonAdditionalWork(lhsValue, rhsValue, this)
                        },
                        falseBranch = mkIte(
                            condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaBoolType) },
                            trueBranch = run {
                                val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, boolSort)) }
                                commonAdditionalWork(lhsValue, rhsValue, this)
                            },
                            falseBranch = commonAdditionalWork(lhsValue, rhs, this)
//                            falseBranch = mkIte(
//                                condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaStringType) },
//                                trueBranch = run { error("TODO") },
//                                falseBranch = commonAdditionalWork(lhsValue, rhs, this)
//                            )
                        )
                    )
                },
                falseBranch = mkIte(
                    condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaBoolType) },
                    trueBranch = run {
                        val lhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, boolSort)) }
                        mkIte(
                            condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaNumberType) },
                            trueBranch = run {
                                val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, fp64Sort)) }
                                commonAdditionalWork(lhsValue, rhsValue, this)
                            },
                            falseBranch = mkIte(
                                condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaBoolType) },
                                trueBranch = run {
                                    val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, boolSort)) }
                                    commonAdditionalWork(lhsValue, rhsValue, this)
                                },
//                                falseBranch = mkIte(
//                                    condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaStringType) },
//                                    trueBranch = run { error("TODO") },
//                                    falseBranch = commonAdditionalWork(lhsValue, rhs, scope)
//                                )
                                falseBranch = commonAdditionalWork(lhsValue, rhs, this)
                            )
                        )
                    },
                    falseBranch = mkIte(
                        condition = calcOnState { memory.types.evalIsSubtype(lhs, PandaStringType) },
                        trueBranch = run { error("TODO") },
                        falseBranch = mkIte(
                            condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaNumberType) },
                            trueBranch = run {
                                val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(lhs, fp64Sort)) }
                                commonAdditionalWork(lhs, rhsValue, this)
                            },
                            falseBranch = mkIte(
                                condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaBoolType) },
                                trueBranch = run {
                                    val rhsValue = calcOnState { memory.read(ctx.constructAuxiliaryFieldLValue(rhs, boolSort)) }
                                    commonAdditionalWork(lhs, rhsValue, this)
                                },
//                                falseBranch = mkIte(
//                                    condition = calcOnState { memory.types.evalIsSubtype(rhs, PandaStringType) },
//                                    trueBranch = run { error("TODO") },
//                                    falseBranch = commonAdditionalWork(lhs, rhs, scope)
//                                )
                                falseBranch = run {
                                    commonAdditionalWork(lhs, rhs, this)
                                }
                            )
                        )
                    )
                )
            )
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
        TODO()
    }

    private fun numberToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun boolToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun boolToBool(lhs: UExpr<KBoolSort>, rhs: UExpr<KBoolSort>, scope: PandaStepScope): UConcreteHeapRef {
        val value = with(lhs.pctx) { onBool(lhs, rhs) }
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaNumberType) }
        scope.doWithState { memory.write(lhs.pctx.constructAuxiliaryFieldLValue(newAddr, ctx.boolSort), value) }
        return newAddr
    }

    private fun boolToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun boolToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun stringToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun stringToBool(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun stringToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun stringToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun objectToNumber(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun objectToBool(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun objectToString(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }

    private fun objectToObject(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: PandaStepScope): UConcreteHeapRef {
        TODO()
    }
}

fun PandaUExprWrapper.withSort(ctx: PandaContext, sort: USort): PandaUExprWrapper {
    val newUExpr = when (from) {
        is PandaNumberConstant -> from.withSort(ctx, sort, uExpr)
        else -> uExpr
    }

    return PandaUExprWrapper(from, newUExpr)
}

fun PandaNumberConstant.withSort(
    ctx: PandaContext,
    sort: USort,
    default: UExpr<out USort>,
): UExpr<out USort> = when (sort) {
    is PandaBoolSort -> ctx.mkBool(value == 1)
    else -> default
}