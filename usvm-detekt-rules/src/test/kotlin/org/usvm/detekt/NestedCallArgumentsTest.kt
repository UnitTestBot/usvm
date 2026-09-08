package org.usvm.detekt

import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NestedCallArgumentsTest {
    private val rule = NestedCallArguments()

    @Test
    fun `allows two nested call levels`() {
        val code = """
            fun result(diagnostic: Diagnostic): Result = Result(
                diagnostics = listOf(diagnostic),
            )
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports three nested call levels`() {
        val code = """
            fun result(): Result = Result(
                diagnostics = listOf(
                    Diagnostic(),
                ),
            )
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports only the outer call for deeper nesting`() {
        val code = """
            fun result(): String = outer(
                middle(
                    inner(
                        leaf(),
                    ),
                ),
            )
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `counts a qualified call used as an argument`() {
        val code = """
            fun result(factory: Factory): List<Value> = resultOf(
                listOf(
                    factory.create(),
                ),
            )
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `does not count fluent receiver calls as nested arguments`() {
        val code = """
            fun result(source: Source): Value = source.read().parse().normalize()
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }

    @Test
    fun `allows compact nested calls`() {
        val code = """
            fun result(): Result = Result(listOf(Diagnostic()))
        """.trimIndent()

        val findings = rule.compileAndLint(code)

        assertEquals(0, findings.size)
    }
}
