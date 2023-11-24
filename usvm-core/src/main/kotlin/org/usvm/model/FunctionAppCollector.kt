package org.usvm.model

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFunctionApp
import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf

class FunctionAppCollector<S : KSort>(
    ctx: KContext,
    val function: KDecl<S>
) : KNonRecursiveVisitor<PersistentSet<KFunctionApp<S>>>(ctx) {
    override fun <T : KSort> defaultValue(expr: KExpr<T>): PersistentSet<KFunctionApp<S>> = persistentHashSetOf()

    override fun mergeResults(
        left: PersistentSet<KFunctionApp<S>>,
        right: PersistentSet<KFunctionApp<S>>
    ): PersistentSet<KFunctionApp<S>> =
        when {
            left.isEmpty() && right.isEmpty() -> persistentHashSetOf()
            left.size < right.size -> right.addAll(left)
            else -> left.addAll(right)
        }

    override fun <T : KSort> visit(expr: KFunctionApp<T>): KExprVisitResult<PersistentSet<KFunctionApp<S>>> =
        visitExprAfterVisitedDefault(expr, expr.args) {
            if (function == it.decl) {
                saveVisitResult(it, persistentHashSetOf(it.uncheckedCast()))
            } else {
                KExprVisitResult.EMPTY
            }
        }
}
