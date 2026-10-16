package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DslAutomaticConfirmationTest {
    @TempDir
    lateinit var directory: Path

    private val configuration = WitnessReplayConfiguration(timeoutMillis = 15_000, validationReserveMillis = 3_000)

    @Test
    fun `native discovery selects the actual named check and shrinking cannot replace it with an earlier false`() {
        val compiled = compile(program())

        val evidence = confirm(compiled, value = 80.0)

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, evidence.status, evidence.diagnostic)
        assertEquals("threshold", evidence.candidate.target.id)
        assertEquals(WitnessOracleOutcome.FALSE, evidence.candidate.target.expectedOutcome)
        assertEquals(listOf(JsConcreteValue.number(15.0)), evidence.reduction.inputs)
        assertTrue(evidence.reduction.acceptedShrinks > 0)
        assertEquals("threshold", evidence.originalReplay?.failure?.checkId)
        assertEquals("threshold", evidence.reducedReplay?.failure?.checkId)
    }

    @Test
    fun `holding inputs and rejected admission do not trigger random generation`() {
        val compiled = compile(program())
        val cases = listOf(
            11.0 to WitnessReplayStatus.PROPERTY_HOLDS,
            0.0 to WitnessReplayStatus.PRECONDITION_REJECTED,
            101.0 to WitnessReplayStatus.DOMAIN_REJECTED,
        )

        for ((value, expected) in cases) {
            val evidence = confirm(compiled, value = value)

            assertEquals(expected, evidence.status, "input=$value: ${evidence.diagnostic}")
            assertEquals(listOf(JsConcreteValue.number(value)), evidence.candidate.inputs)
            assertNull(evidence.reduction.inputs)
            assertEquals(0, evidence.reduction.attempts)
        }
    }

    @Test
    fun `native named exception retains its outcome and original error while shrinking`() {
        val compiled = compile(program(functionName = "fail"))

        val falseTarget = confirm(compiled, value = 80.0)
        val evidence = confirm(compiled, value = 80.0, outcome = WitnessOracleOutcome.THREW)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, falseTarget.status)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, evidence.status, evidence.diagnostic)
        assertEquals("threshold", evidence.candidate.target.id)
        assertEquals(WitnessOracleOutcome.THREW, evidence.candidate.target.expectedOutcome)
        assertEquals(listOf(JsConcreteValue.number(15.0)), evidence.reduction.inputs)
        assertEquals("TypeError", evidence.reducedReplay?.failure?.errorName)
        assertEquals("original exception", evidence.reducedReplay?.failure?.message)
    }

    @Test
    fun `exception before a named check cannot become its automatically selected target`() {
        val program = defineProperty(id = "dsl.local-exception") {
            val fail = function<(Double) -> Double>(module = "subject.ts", exportName = "fail")
            val value by int(min = 0, max = 100)
            val result by value(fail(value))

            check(name = "threshold") { result equalTo value }
        }
        val compiled = compile(program)

        val evidence = confirm(compiled, value = 80.0, outcome = WitnessOracleOutcome.THREW)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, evidence.status)
        assertNull(evidence.originalReplay?.failure?.checkId)
        assertNull(evidence.reduction.inputs)
        assertEquals(compiled.definition.predicate.exportName, evidence.candidate.target.id)
    }

    @Test
    fun `ordinary DSL run reports an exception before its first check as inconclusive`() {
        Files.writeString(directory.resolve("subject.ts"), SUBJECT)
        val program = defineProperty(id = "dsl.runner-local-exception") {
            val fail = function<(Double) -> Double>(module = "subject.ts", exportName = "fail")
            val value by int(min = 0, max = 100)
            val result by value(fail(value))

            check(name = "threshold") { result equalTo value }
        }

        val run = DslPropertyRunner(sourceRoots = listOf(directory)).run(
            program = program,
            engine = PropertyEngine.USVM,
            configuration = PropertyRunConfiguration(timeoutMillis = 60_000),
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, run.symbolic?.status)
        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, run.symbolicReplay?.status)
        assertNull(run.symbolicReplay?.originalReplay?.failure?.checkId)
        assertFailsWith<DslPropertyExecutionError> { run.requireSuccess() }
    }

    @Test
    fun `expired discovery deadline does not start a fresh native process`() {
        val compiled = compile(program())
        val deadline = WitnessReplayDeadline(timeoutMillis = 1)
        Thread.sleep(5)

        val evidence = confirm(
            compiled = compiled,
            value = 80.0,
            deadline = deadline,
            nodeExecutable = "definitely-not-a-node-executable",
        )

        assertEquals(WitnessReplayStatus.TIMEOUT, evidence.status)
        assertNull(evidence.originalReplay)
        assertNull(evidence.reduction.inputs)
    }

    private fun program(functionName: String = "threshold") = defineProperty(id = "dsl.automatic-check") {
        val threshold = function<(Double) -> Double>(module = "subject.ts", exportName = functionName)
        val value by int(min = 0, max = 100)

        assume { value greaterOrEqual 5 }
        check(name = "earlier") { value greaterOrEqual 10 }
        check(name = "threshold") { threshold(value) equalTo value }
    }

    private fun compile(program: DslProperty): CompiledDslProperty {
        Files.writeString(directory.resolve("subject.ts"), SUBJECT)
        val compiled = PropertyTypeScriptCompiler().compile(
            program = program,
            generatedModule = GENERATED_MODULE,
            resolveModule = { module -> "./$module" },
        )
        Files.writeString(directory.resolve(GENERATED_MODULE), compiled.typeScript)

        return compiled
    }

    private fun confirm(
        compiled: CompiledDslProperty,
        value: Double,
        outcome: WitnessOracleOutcome = WitnessOracleOutcome.FALSE,
        deadline: WitnessReplayDeadline = WitnessReplayDeadline(configuration.timeoutMillis),
        nodeExecutable: String = "node",
    ) = FastCheckBackend(sourceRoots = listOf(directory), nodeExecutable = nodeExecutable).confirmOriginalWitness(
        property = compiled.definition,
        candidate = compiled.definition.captureWitness(
            inputs = listOf(JsConcreteValue.number(value)),
            target = WitnessTarget(id = compiled.definition.predicate.exportName, expectedOutcome = outcome),
            sources = listOf(GENERATED_MODULE, "subject.ts").map { module ->
                val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(module)))
                    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
                PropertyObservationSource(module = module, sha256 = hash)
            },
            provenance = "original-check-discovery-fixture",
        ),
        configuration = configuration,
        deadline = deadline,
    )

    private companion object {
        const val GENERATED_MODULE = "property.ts"
        val SUBJECT = """
            export function threshold(value: number): number {
                if (value < 15) return value;
                return value + 1;
            }
            export function fail(value: number): number {
                if (value < 15) throw new TypeError('different exception');
                throw new TypeError('original exception');
            }
        """.trimIndent()
    }
}
