package org.usvm

import io.ksmt.KContext
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.usvm.api.readField
import org.usvm.memory.URegisterStackLValue

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>
) {

    private val ctx = baseExpr.tctx

    @Suppress("UNCHECKED_CAST")
    fun generateAll(typeSystem: TSTypeSystem, scope: TSStepScope): List<UExpr<out USort>> = when (val sort = baseExpr.sort) {
        ctx.addressSort -> with(ctx) {
            val heapRef = baseExpr as UHeapRef
            listOf(
                mkIte(
                    condition = scope.calcOnState { memory.types.evalIsSubtype(heapRef, EtsNumberType) },
                    trueBranch = run {
                        val value = scope.calcOnState { memory. }
                    },
                    falseBranch = mkIte(
                        condition = scope.calcOnState { memory.types.evalIsSubtype(heapRef, EtsBooleanType) },
                        trueBranch = run {

                        },
                        falseBranch = run {

                        }
                    )
                )
            )
        }

        else -> listOf(asFp64(), asBool())
    }

    @Suppress("UNCHECKED_CAST")
    fun asFp64(): UExpr<KFp64Sort> = when (baseExpr.sort) {
        ctx.fp64Sort -> baseExpr as UExpr<KFp64Sort>
        ctx.boolSort -> if (extractBool(baseExpr)) ctx.mkFp64(1.0) else ctx.mkFp64(0.0)
        else -> ctx.mkFp64(0.0)
    }

    fun asBool(): UExpr<KBoolSort> {

    }
}
