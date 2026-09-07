package org.usvm.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/** Prevents deeply nested calls such as `outer(middle(inner()))`. */
internal class NestedCallArguments(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NestedCallArguments",
        severity = Severity.Style,
        description = "Nested call arguments should be extracted into intermediate variables",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.isArgumentRootCall()) {
            return
        }
        if (!expression.textContains('\n')) {
            return
        }

        val depth = expression.argumentCallDepth()
        if (depth <= MAX_ARGUMENT_CALL_DEPTH) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "Call arguments are nested $depth levels deep; extract an inner call into a variable",
            ),
        )
    }
}

private fun KtCallExpression.argumentCallDepth(): Int {
    val nestedDepth = valueArguments
        .asSequence()
        .mapNotNull { argument -> argument.getArgumentExpression()?.rootCallOrNull() }
        .maxOfOrNull { nestedCall -> nestedCall.argumentCallDepth() }
        ?: 0

    return nestedDepth + 1
}

private fun KtExpression.rootCallOrNull(): KtCallExpression? = when (this) {
    is KtCallExpression -> this
    is KtParenthesizedExpression -> expression?.rootCallOrNull()
    is KtQualifiedExpression -> selectorExpression?.rootCallOrNull()
    else -> null
}

private fun KtCallExpression.isArgumentRootCall(): Boolean {
    var current: KtExpression = this
    while (true) {
        val parentExpression = current.parent as? KtExpression ?: break
        val wrapsCurrent = when (parentExpression) {
            is KtParenthesizedExpression -> parentExpression.expression == current
            is KtQualifiedExpression -> parentExpression.selectorExpression == current
            else -> false
        }
        if (!wrapsCurrent) {
            break
        }

        current = parentExpression
    }

    return current.parent is KtValueArgument
}

private const val MAX_ARGUMENT_CALL_DEPTH = 2
