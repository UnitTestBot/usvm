package org.usvm

import com.jetbrains.rd.framework.base.deepClonePolymorphic
import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsType
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>,
    private val scope: TSStepScope,
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>?> = mutableMapOf(baseExpr.sort to baseExpr)

    private val ctx = baseExpr.tctx

    init {
        if (baseExpr.sort == ctx.addressSort) {
            TSTypeSystem.primitiveTypes.forEach { transform(ctx.typeToSort(it)) }
        }
    }

    fun transform(sort: USort): UExpr<out USort>? = with(ctx) {
        when (sort) {
            fp64Sort -> asFp64()
            boolSort -> asBool()
            addressSort -> asRef()
            else -> error("")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun intersectWithTypeCoercion(
        other: TSExprTransformer,
        action: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?
    ): UExpr<out USort> {
        intersect(other)

        val exprs = exprCache.keys.mapNotNull { sort ->
            val lhv = transform(sort)
            val rhv = other.transform(sort)
            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else null
        }

        return if (exprs.size > 1) {
            assert(exprs.all { it.sort == ctx.boolSort })
            ctx.mkAnd(exprs as List<UBoolExpr>)
        } else exprs.single()
    }

    fun intersect(other: TSExprTransformer) {
        exprCache.keys.forEach { sort ->
            other.transform(sort)
        }
//        other.exprCache.keys.forEach { sort ->
//            transform(sort)
//        }
    }

    fun asFp64(): UExpr<KFp64Sort> = exprCache.getOrPut(ctx.fp64Sort) {
        when (baseExpr.sort) {
            ctx.fp64Sort -> baseExpr
            ctx.boolSort -> ctx.boolToFpSort(baseExpr.cast())
            ctx.addressSort -> with(ctx) {
                val value = TSRefTransformer(ctx, scope.calcOnState { memory }, fp64Sort).apply(baseExpr.cast()) as URegisterReading
                mkIte(
                    condition = scope.calcOnState { memory.types.evalIsSubtype(baseExpr.cast(), EtsNumberType) },
                    trueBranch = value,
                    falseBranch = ctx.mkFp64NaN().cast()
                ).also {
                    scope.calcOnState { memory.write(URegisterStackLValue(fp64Sort, value.idx), it) }
                }
            }

            else -> ctx.mkFp64(0.0)
        }
    }.cast()

    fun asBool(): UExpr<KBoolSort> = exprCache.getOrPut(ctx.boolSort) {
        when (baseExpr.sort) {
            ctx.boolSort -> baseExpr
            ctx.fp64Sort -> with(ctx) { mkIte(mkFpEqualExpr(baseExpr.cast(), mkFp64(1.0)), mkTrue(), mkFalse()) }
            ctx.addressSort -> with(ctx) {
                val value = TSRefTransformer(ctx, scope.calcOnState { memory }, boolSort).apply(baseExpr.cast()) as URegisterReading
                mkIte(
                    condition = scope.calcOnState { memory.types.evalIsSubtype(baseExpr.cast(), EtsBooleanType) },
                    trueBranch = value,
                    falseBranch = ctx.mkFalse().cast()
                ).also {
                    scope.calcOnState { memory.write(URegisterStackLValue(boolSort, value.idx), it) }
                }
            }

            else -> ctx.mkFalse()
        }
    }.cast()

    fun asRef(): UExpr<UAddressSort>? = exprCache.getOrPut(ctx.addressSort) {
        when (baseExpr.sort) {
            ctx.addressSort -> baseExpr
            else -> null
        }
    }.cast()

    class TSRefTransformer(
        private val ctx: TSContext,
        private val memory: UReadOnlyMemory<EtsType>,
        private val sort: USort,
    ) {

        fun apply(expr: UExpr<UAddressSort>): UExpr<USort> = when (expr) {
            is URegisterReading -> transform(expr)
            else -> error("Not yet implemented: $expr")
        }

        fun transform(expr: URegisterReading<UAddressSort>): UExpr<USort> =
            memory.read(URegisterStackLValue(sort, expr.idx))

    }
}
