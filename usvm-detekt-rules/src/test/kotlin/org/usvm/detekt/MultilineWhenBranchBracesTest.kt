package org.usvm.detekt

import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultilineWhenBranchBracesTest {
    private val rule = MultilineWhenBranchBraces()

    @Test
    fun `allows branch body on the same line`() {
        val code = """
            fun choose(condition: Boolean): Int = when {
                condition -> 1
                else -> 0
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports branch body moved to a new line without braces`() {
        val code = """
            fun choose(condition: Boolean): Int = when {
                condition ->
                    1
                else -> 0
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows branch body moved to a new line inside braces`() {
        val code = """
            fun choose(condition: Boolean): Int = when {
                condition -> {
                    1
                }
                else -> 0
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports else body moved to a new line without braces`() {
        val code = """
            fun choose(condition: Boolean): Int = when {
                condition -> 1
                else ->
                    0
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows multiline body inside braces`() {
        val code = """
            fun choose(condition: Boolean): Int = when {
                condition -> {
                    println("selected")
                    1
                }
                else -> 0
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }
}
