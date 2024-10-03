package org.usvm.collection.set

import io.ksmt.decl.KFuncDecl
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isFalse
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UUpdateNode
import org.usvm.solver.UExprTranslator

internal abstract class USetUpdatesTranslator<Element>(
    val exprTranslator: UExprTranslator<*, *>,
    val selectKey: Element,
) : UMemoryUpdatesVisitor<Element, UBoolSort, KExpr<KBoolSort>> {
    override fun visitSelect(result: KExpr<KBoolSort>, key: Element): UExpr<UBoolSort> {
        error("Unsupported operation")
    }

    override fun visitUpdate(
        previous: KExpr<KBoolSort>,
        update: UUpdateNode<Element, UBoolSort>
    ): KExpr<KBoolSort> = with(exprTranslator.ctx) {
        if (update.guard.isFalse) return previous

        when (update) {
            is UPinpointUpdateNode -> {
                val key = update.keyInfo.mapKey(update.key, exprTranslator)

                val value = update.value.translated
                val guard = update.guard.translated

                val condition = mkAnd(
                    update.keyInfo.eqSymbolic(this, key, selectKey),
                    guard,
                    flat = false
                )

                mkIte(condition, value, previous)
            }

            is URangedUpdateNode<*, *, Element, *, UBoolSort> -> {
                val otherSetContains = update.includesSymbolically(
                    key = selectKey, composer = null
                ).translated // already includes guard

                mkOr(otherSetContains, previous, flat = false)
            }
        }
    }

    val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}

internal class UAllocatedSetUpdatesTranslator<ElementSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    selectKey: UExpr<ElementSort>
) : USetUpdatesTranslator<UExpr<ElementSort>>(exprTranslator, selectKey) {
    override fun visitInitialValue(): KExpr<KBoolSort> = selectKey.ctx.falseExpr
}

internal class UInputSetUpdatesTranslator<ElementSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    private val initialFunction: KFuncDecl<UBoolSort>,
    selectKey: USymbolicSetElement<ElementSort>
) : USetUpdatesTranslator<USymbolicSetElement<ElementSort>>(exprTranslator, selectKey) {
    override fun visitInitialValue(): KExpr<KBoolSort> = with(initialFunction.ctx) {
        mkApp(initialFunction, listOf(selectKey.first, selectKey.second))
    }
}
