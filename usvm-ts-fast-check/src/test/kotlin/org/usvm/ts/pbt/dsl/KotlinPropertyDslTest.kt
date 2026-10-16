package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinPropertyDslTest {
    private val roots = listOf(testResourcesRoot().resolve("properties/dsl"))
    private val runner = DslPropertyRunner(sourceRoots = roots)

    // Functional assertions include cold frontend initialization in the runner's shared timeout.
    private val configuration = PropertyRunConfiguration(seed = 42, numRuns = 50)

    @Test
    fun `calculator runs through existing concrete and symbolic execution`() {
        val program = addition(exportName = "add")

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        run.requireSuccess()
        assertEquals(PropertyRunStatus.SUCCESS, run.concrete?.status)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, run.symbolic?.status)
        assertEquals(listOf("a", "b"), program.inputs.map { it.name })
        assertEquals(listOf("commutativity", "addition"), program.checks.map { it.name })
    }

    @Test
    fun `named failure shrinks and replays through the same generated property`() {
        val program = addition(exportName = "brokenAdd")

        val run = runner.run(program = program, configuration = configuration)
        val result = assertNotNull(run.concrete)
        val failure = assertNotNull(result.failure)
        val replay = runner.run(
            program = program,
            configuration = configuration.copy(seed = result.seed, replayPath = result.replayPath),
        )

        assertEquals(PropertyFailureKind.PROPERTY, failure.kind)
        assertEquals("addition", failure.checkId)
        assertEquals(listOf(JsConcreteValue.number(0.0), JsConcreteValue.number(0.0)), result.counterexample)
        assertEquals(JsConcreteValue.number(1.0), failure.actual)
        assertEquals(JsConcreteValue.number(0.0), failure.expected)
        assertTrue(result.numShrinks > 0)
        assertEquals(result.counterexample, replay.concrete?.counterexample)
        assertEquals(failure, replay.concrete?.failure)
        val error = assertFailsWith<DslPropertyAssertionError> { run.requireSuccess() }
        assertTrue(error.message.orEmpty().contains("KotlinPropertyDslTest.kt"))
        assertTrue(error.message.orEmpty().contains("check addition"))
        assertTrue(error.message.orEmpty().contains("a=0, b=0"))
        assertTrue(error.message.orEmpty().contains("actual=1, expected=0"))
    }

    @Test
    fun `symbolic counterexample is concretely validated and minimized`() {
        val run = runner.run(
            program = addition(exportName = "brokenAdd"),
            engine = PropertyEngine.USVM,
            configuration = configuration,
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, run.symbolic?.status)
        val replay = assertNotNull(run.symbolicReplay)
        assertEquals("addition", replay.failure?.checkId)
        assertEquals(listOf(JsConcreteValue.number(0.0), JsConcreteValue.number(0.0)), replay.counterexample)
        assertFailsWith<DslPropertyAssertionError> { run.requireSuccess() }
    }

    @Test
    fun `admission and shared values preserve the author relation`() {
        val program = defineProperty(id = "calculator.division") {
            val divide = function<(Double, Double) -> Double>(module = "calculator.ts", exportName = "divide")
            val a by int(min = -10, max = 10)
            val b by int(min = -10, max = 10)

            assume { (b notEqualTo 0) and ((a greaterOrEqual 0) or (a lessThan 0)) }
            val quotient by value(divide(a, b))

            check(name = "division") { quotient equalTo (a / b) }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        run.requireSuccess()
        assertTrue(program.assumptions.isNotEmpty())
        assertEquals("quotient", program.locals.single().name)
    }

    @Test
    fun `rejected inputs remain distinct from a check violation`() {
        val program = defineProperty(id = "calculator.rejected") {
            val a by int(min = 0, max = 1)

            assume { a lessThan 0 }
            check(name = "unreachable") { a equalTo 99 }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        assertEquals(PropertyFailureKind.PRECONDITION_EXHAUSTED, run.concrete?.failure?.kind)
        assertEquals(UsvmPropertySearchStatus.PRECONDITION_REJECTED, run.symbolic?.status)
        assertEquals(null, run.concrete?.counterexample)
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    @Test
    fun `arithmetic and strict equality use JavaScript values`() {
        val program = defineProperty(id = "calculator.numeric-semantics") {
            val a by number()

            check(name = "nan") { number(Double.NaN) notEqualTo number(Double.NaN) }
            check(name = "signed-zero") { number(-0.0) equalTo number(0.0) }
            check(name = "overflow") { (number(Int.MAX_VALUE) + 1) equalTo 2_147_483_648.0 }
            check(name = "fraction") { (number(1) / 2) equalTo 0.5 }
            check(name = "remainder") { (number(3) % 2) equalTo 1 }
            check(name = "reflexivity") { (a equalTo a) or (a notEqualTo a) }
        }
        val examples = listOf(-0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).map { value ->
            listOf(JsConcreteValue.number(value))
        }

        val run = runner.run(
            program = program,
            engine = PropertyEngine.BOTH,
            configuration = configuration.copy(examples = examples),
        )

        run.requireSuccess()
    }

    @Test
    fun `builder rejects accidental Kotlin equality and impure admission`() {
        assertFailsWith<IllegalStateException> {
            defineProperty(id = "invalid.equality") {
                val a by int()

                @Suppress("UNUSED_VARIABLE")
                val hostEquality = a == a
            }
        }
        assertFailsWith<IllegalArgumentException> {
            defineProperty(id = "invalid.admission") {
                val identity = function<(Double) -> Double>(module = "calculator.ts", exportName = "identity")
                val a by int()

                assume { identity(a) greaterThan 0 }
                check(name = "identity") { identity(a) equalTo a }
            }
        }
    }

    @Test
    fun `logical expressions short circuit existing function calls`() {
        val program = defineProperty(id = "calculator.short-circuit") {
            val unreachable = function<(Double) -> Double>(module = "calculator.ts", exportName = "unreachable")
            val a by int(min = -2, max = 2)

            check(name = "or") { (a equalTo a) or (unreachable(a) equalTo 0) }
            check(name = "and") { !((a notEqualTo a) and (unreachable(a) equalTo 0)) }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        run.requireSuccess()
    }

    @Test
    fun `primitive inputs and UTF16 literals retain their JavaScript values`() {
        val program = defineProperty(id = "calculator.primitives") {
            val flag by boolean()
            val text by string(minLength = 0, maxLength = 2)

            check(name = "boolean") { (flag or !flag) equalTo true }
            check(name = "string") { text equalTo text }
            check(name = "surrogate") { string("\uD800") equalTo "\uD800" }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        run.requireSuccess()
    }

    @Test
    fun `escaping exceptions retain the active check and original error`() {
        val program = defineProperty(id = "calculator.exception") {
            val fail = function<(Double) -> Double>(module = "calculator.ts", exportName = "unreachable")
            val a by int(min = -2, max = 2)

            check(name = "unexpected") { fail(a) equalTo a }
        }

        val run = runner.run(program = program, engine = PropertyEngine.BOTH, configuration = configuration)

        val diagnostic = run.executionDiagnostic()
        assertEquals("unexpected", run.concrete?.failure?.checkId, diagnostic)
        assertEquals("Error", run.concrete?.failure?.errorName, diagnostic)
        val replay = assertNotNull(run.symbolicReplay, diagnostic)
        assertEquals("unexpected", replay.failure?.checkId, diagnostic)
        assertFailsWith<DslPropertyAssertionError> { run.requireSuccess() }
    }

    @Test
    fun `expired total deadline skips symbolic analysis and cannot report success`() {
        val program = addition(exportName = "add")

        val run = runner.run(
            program = program,
            engine = PropertyEngine.USVM,
            configuration = configuration.copy(timeoutMillis = 1),
        )

        val diagnostic = run.executionDiagnostic()
        assertTrue(run.symbolicSkippedDueToDeadline, diagnostic)
        assertNull(run.concrete, diagnostic)
        assertNull(run.symbolic, diagnostic)
        assertNull(run.symbolicReplay, diagnostic)
        assertTrue(run.elapsedMillis >= 1, diagnostic)
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    private fun DslPropertyRun.executionDiagnostic(): String =
        "elapsedMillis=$elapsedMillis, budgetMillis=$budgetMillis, " +
            "symbolicSkippedDueToDeadline=$symbolicSkippedDueToDeadline, symbolicStatus=${symbolic?.status}, " +
            "concreteStatus=${concrete?.status}, replayStatus=${symbolicReplay?.status}, " +
            "replayFailure=${symbolicReplay?.failure}"

    private fun addition(exportName: String): DslProperty = defineProperty(id = "calculator.$exportName") {
        val sum = function<(Double, Double) -> Double>(module = "calculator.ts", exportName = exportName)
        val a by int(min = -100, max = 100)
        val b by int(min = -100, max = 100)

        check(name = "commutativity") { sum(a, b) equalTo sum(b, a) }
        check(name = "addition") { sum(a, b) equalTo (a + b) }
    }
}
