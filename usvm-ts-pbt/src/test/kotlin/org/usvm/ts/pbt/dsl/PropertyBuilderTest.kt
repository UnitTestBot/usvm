package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.NumberDomain
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PropertyBuilderTest {
    @Test
    fun `integer domains remain JavaScript numeric expressions`() {
        val program = defineProperty(id = "builder.integer") {
            val a by int(min = -4, max = 7)

            check(name = "range") { (a greaterOrEqual -4) and (a lessOrEqual 7) }
        }

        assertEquals(IntegerDomain(min = -4, max = 7), program.inputs.single().domain)
        assertEquals("a", program.inputs.single().name)
        assertEquals("PropertyBuilderTest.kt", program.checks.single().source.fileName)
        assertTrue(program.checks.single().source.line > 0)
    }

    @Test
    fun `literal and bounded number declarations resolve without ambiguity`() {
        val program = defineProperty(id = "builder.number") {
            val a by number(min = -0.5, max = 0.5)

            check(name = "bound") { a lessOrEqual number(0.5) }
        }

        val domain = program.inputs.single().domain as NumberDomain
        assertEquals(-0.5, domain.min.toDouble())
        assertEquals(0.5, domain.max.toDouble())
        assertEquals(false, domain.allowNaN)
    }

    @Test
    fun `function references and named checks are validated while building`() {
        assertFailsWith<IllegalArgumentException> {
            defineProperty(id = "builder.invalid-reference") {
                function<(Double) -> Double>(module = "../escape.ts", exportName = "add")
            }
        }
        assertFailsWith<IllegalStateException> {
            defineProperty(id = "builder.host-integer") {
                function<(Int) -> Int>(module = "calculator.ts", exportName = "add")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            defineProperty(id = "builder.duplicate-check") {
                val a by int()

                check(name = "same") { a equalTo a }
                check(name = "same") { a equalTo a }
            }
        }
    }

    @Test
    fun `expressions cannot escape into a different property`() {
        lateinit var captured: PropertyExpression<Double>
        defineProperty(id = "builder.first") {
            val a by int()
            captured = a

            check(name = "same") { a equalTo a }
        }

        assertFailsWith<IllegalArgumentException> {
            defineProperty(id = "builder.second") {
                val b by int()

                check(name = "mixed") { captured equalTo b }
            }
        }
    }

    @Test
    fun `lowering retains expression occurrences original checks and a compatible manifest`() {
        val program = defineProperty(id = "builder.lowering") {
            val sum = function<(Double, Double) -> Double>(module = "calculator.ts", exportName = "add")
            val a by int()
            val b by int()

            check(name = "addition") { sum(a, b) equalTo (a + b) }
            check(name = "commutativity") { sum(a, b) equalTo sum(b, a) }
        }
        val compiled = PropertyTypeScriptCompiler().compile(
            program = program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
        )
        val check = program.checks.first().expression.term as PropertyExpressionTerm.Operation
        val call = check.arguments.first()
        val locations = compiled.expressionLocations.getValue(call.id)
        val lines = compiled.typeScript.lines()

        assertEquals(2, locations.size)
        locations.forEach { range ->
            val text = lines[range.start.line - 1].substring(range.start.column - 1, range.endColumn - 1)
            assertEquals("__usvmFunction0(a, b)", text)
        }
        assertEquals(program, compiled.program)
        assertEquals(program.checks.map { it.name }, compiled.definition.assertions.map { it.id })
        val manifest = compiled.definition.toManifest()
        assertEquals(manifest, PropertyManifestJson.decode(PropertyManifestJson.encode(manifest)))
        assertEquals("__usvmCheckResult", manifest.predicate.checkResultExportName)
    }
}
