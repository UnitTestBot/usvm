package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DslNumericLocalCompilerTest {
    @Test
    fun `capture is opt in and its receipt identifies both generated assignments`() {
        val program = program()
        val compiler = PropertyTypeScriptCompiler()

        val ordinary = compiler.compile(program, generatedModule = "generated.ts", resolveModule = { "./$it" })
        val compiled = compiler.compile(
            program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
            numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "identity"),
        )
        val capture = assertNotNull(compiled.numericLocalCapture)
        val binding = capture.binding
        val lines = compiled.typeScript.lines()
        val hash = MessageDigest.getInstance("SHA-256").digest(compiled.typeScript.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        assertNull(ordinary.numericLocalCapture)
        assertTrue("__usvmObserve" !in ordinary.typeScript)
        assertEquals(hash, binding.sourceSha256)
        assertEquals(program.id.value, binding.programId)
        assertEquals(program.locals.single().expression.id, binding.nodeId)
        assertEquals("__usvmPredicate", binding.predicateExportName)
        assertEquals("__usvmCheckResult", binding.companionExportName)
        listOf(binding.predicateAssignment, binding.companionAssignment).forEach { point ->
            assertTrue(lines[point.line - 1].startsWith("  const result = "))
        }
        val predicate = compiled.typeScript.substringBefore("export function __usvmCheckResult")
        assertTrue("__usvmObserve" !in predicate)
        assertTrue(binding.predicateAssignment.line < binding.companionAssignment.line)
        assertEquals(0, binding.occurrence)
        assertEquals(capture.result.callSite, compiled.definition.assertions.first().testedCall)
        assertEquals(capture.binding, capture.result.compilerLocal)
        assertEquals(0, capture.input.inputIndex)
        val callSite = capture.result.callSite
        assertTrue(lines[callSite.line - 1].substring(callSite.column - 1).endsWith("(input);"))
    }

    @Test
    fun `unsupported local and check shapes are rejected instead of silently broadening capture`() {
        val requests = listOf(
            DslNumericLocalCapture(localName = "missing", checkName = "identity"),
            DslNumericLocalCapture(localName = "result", checkName = "missing"),
            DslNumericLocalCapture(localName = "result", checkName = "nested"),
        )
        requests.forEach { request ->
            assertFailsWith<IllegalArgumentException> {
                PropertyTypeScriptCompiler().compile(
                    program(),
                    generatedModule = "generated.ts",
                    resolveModule = { "./$it" },
                    numericLocalCapture = request,
                )
            }
        }
        val arithmetic = defineProperty(id = "dsl.arithmetic") {
            val input by int()
            val result by value(input + 1)

            check(name = "identity") { result equalTo input }
        }

        assertFailsWith<IllegalArgumentException> {
            PropertyTypeScriptCompiler().compile(
                arithmetic,
                generatedModule = "generated.ts",
                resolveModule = { "./$it" },
                numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "identity"),
            )
        }
    }

    @Test
    fun `a shared declaration after a check is rejected rather than hoisted`() {
        assertFailsWith<IllegalArgumentException> {
            defineProperty(id = "dsl.no-hoisting") {
                val identity = function<(Double) -> Double>(module = "identity.ts", exportName = "identity")
                val input by int()

                check(name = "first") { input equalTo input }
                @Suppress("UNUSED_VARIABLE")
                val late by value(identity(input))
            }
        }
    }

    private fun program(): DslProperty = defineProperty(id = "dsl.local-capture") {
        val identity = function<(Double) -> Double>(module = "identity.ts", exportName = "identity")
        val input by int()
        val result by value(identity(input))

        check(name = "identity") { result equalTo input }
        check(name = "nested") { (result equalTo input) and (input equalTo input) }
    }
}
