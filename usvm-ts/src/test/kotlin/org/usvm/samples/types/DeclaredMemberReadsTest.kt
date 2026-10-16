package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DeclaredMemberReadsTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/types/DeclaredMemberReads.ts"

    override val scene: EtsScene = loadScene(sourcePath)

    @Test
    fun `getter and method value reads stop with an explicit limitation`() {
        val unsupported = listOf(
            "getter", "inheritedGetter", "methodValue", "inheritedMethodValue",
            "methodWrite", "getterWrite", "setterWrite",
        )
        for (name in unsupported) {
            val method = getMethod(name, className = "DeclaredMemberReads")
            val result = TsMachine(
                scene = scene,
                options = UMachineOptions(
                    solverType = SolverType.YICES,
                    stateCollectionStrategy = StateCollectionStrategy.ALL,
                    stopOnCoverage = 0,
                    timeout = 20.seconds,
                ),
                tsOptions = TsOptions(),
            ).use { it.analyzeWithMetadata(methods = listOf(method)) }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason, name)
            assertTrue(result.states.isEmpty(), name)
            assertFalse(result.engineFailed, name)
            val hasMemberLimitation = result.unsupportedPaths.any { "accessor or method-value semantics" in it }
            assertTrue(hasMemberLimitation, "$name: ${result.unsupportedPaths}")
        }
    }

    @Test
    fun `direct calls own data and own writes retain their values`() {
        val expected = mapOf(
            "directCall" to 5.0,
            "inheritedCall" to 5.0,
            "ownData" to 7.0,
            "ownWrite" to 9.0,
            "callableData" to 11.0,
            "capturedArgumentWrite" to 5.0,
        )

        for ((name, value) in expected) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(name, className = "DeclaredMemberReads"),
                { result -> result.number == value },
                invariants = arrayOf({ result -> result.number == value }),
            )
        }
    }

    @Test
    fun `native replay distinguishes accessors method values and own data`() {
        assertNodeReplay(
            source = getResourcePath(sourcePath).readText() + "\n" + """
                const subject = new DeclaredMemberReads();
                for (const name of ['getter', 'inheritedGetter']) {
                    if (subject[name]() !== 3) throw Error(name);
                }
                for (const name of ['methodValue', 'inheritedMethodValue']) {
                    if (subject[name]() !== 'function') throw Error(name);
                }
                const expected = {
                    directCall: 5, inheritedCall: 5, ownData: 7, ownWrite: 9, callableData: 11,
                    capturedArgumentWrite: 5, setterWrite: 9,
                };
                for (const [name, value] of Object.entries(expected)) {
                    if (subject[name]() !== value) throw Error(name);
                }
                if (subject.methodWrite() !== 9) throw Error('methodWrite');
                let getterWriteThrows = false;
                try { subject.getterWrite(); } catch (error) { getterWriteThrows = error instanceof TypeError; }
                if (!getterWriteThrows) throw Error('getter assignment must throw in a class method');
            """.trimIndent(),
            directory = directory,
            name = "declared-member-reads",
            timeoutMessage = "Declared member native replay timed out",
        )
    }
}
