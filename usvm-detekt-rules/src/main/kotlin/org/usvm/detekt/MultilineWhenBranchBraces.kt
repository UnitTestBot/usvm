package org.usvm.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtWhenEntry

/** Requires a block when a `when` branch body starts below its arrow. */
internal class MultilineWhenBranchBraces(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "MultilineWhenBranchBraces",
        severity = Severity.Style,
        description = "Requires braces when a when branch body starts on a new line",
        debt = Debt.FIVE_MINS,
    )

    override fun visitWhenEntry(entry: KtWhenEntry) {
        super.visitWhenEntry(entry)

        val expression = entry.expression ?: return
        if (expression is KtBlockExpression) {
            return
        }

        val arrow = entry.arrow ?: return
        val fileText = entry.containingFile.text
        val textBeforeExpression = fileText.substring(
            startIndex = arrow.textRange.endOffset,
            endIndex = expression.textRange.startOffset,
        )
        if (!textBeforeExpression.contains('\n') && !textBeforeExpression.contains('\r')) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "A when branch body that starts on a new line must be enclosed in braces",
            ),
        )
    }
}
