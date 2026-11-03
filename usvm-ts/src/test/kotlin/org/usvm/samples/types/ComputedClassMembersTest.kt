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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ComputedClassMembersTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/types/ComputedClassMembers.ts"

    override val scene: EtsScene = loadScene(sourcePath)

    @Test
    fun `ordinary methods execute in plain literal and iterator classes`() {
        for (name in listOf("plainCounter", "literalCounter", "iteratorCounter")) {
            discoverConstant(name, expected = 7.0)
        }
        discoverConstant("literalStatic", expected = 11.0)

        replay(
            """
                const subject = new ComputedClassMembers();
                for (const name of ['plainCounter', 'literalCounter', 'iteratorCounter']) {
                    if (subject[name]() !== 7) throw Error(name);
                }
                if (subject.literalStatic() !== 11) throw Error('literal static');
            """.trimIndent(),
        )
    }

    @Test
    fun `string lookalike calls select their ordinary declarations`() {
        discoverConstant("stringMethod", expected = 29.0)
        assertUnsupported("stringStaticMethod", reason = "Property access needs a modeled object")

        replay(
            """
                const subject = new ComputedClassMembers();
                if (subject.stringMethod() !== 29) throw Error('string method');
                if (subject.stringStaticMethod() !== 43) throw Error('string static');
                if (subject.symbolMethod() !== 17) throw Error('symbol identity');
            """.trimIndent(),
        )
    }

    @Test
    fun `unsupported symbol initialization retains its cause and returns no string result`() {
        assertUnsupported("symbolFieldInitialization", reason = "Symbol-keyed field storage")
    }

    @Test
    fun `symbol access is explicit unsupported instead of returning the string method result`() {
        assertUnsupported("symbolMethod", reason = null)
    }

    @Test
    fun `shadowed and dynamic keys keep frontend barriers`() {
        assertUnsupported("shadowedSymbol", reason = "Unsupported frontend")
        assertUnsupported("dynamicKey", reason = "Unsupported frontend")
    }

    private fun discoverConstant(methodName: String, expected: Double) {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName, className = "ComputedClassMembers"),
            { result -> result.number == expected },
            invariants = arrayOf({ result -> result.number == expected }),
        )
    }

    private fun assertUnsupported(methodName: String, reason: String?) {
        val method = getMethod(methodName, className = "ComputedClassMembers")
        val result = TsMachine(
            scene = scene,
            options = UMachineOptions(
                solverType = SolverType.YICES,
                stateCollectionStrategy = StateCollectionStrategy.ALL,
                stopOnCoverage = 0,
                timeout = 20.seconds,
            ),
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(result.stopReason == TsAnalysisStopReason.EXHAUSTED, "$methodName: ${result.stopReason}")
        assertTrue(result.states.isEmpty(), methodName)
        assertFalse(result.engineFailed, methodName)
        assertTrue(result.unsupportedPaths.isNotEmpty(), "$methodName: $result")
        if (reason != null) {
            assertTrue(result.unsupportedPaths.any { reason in it }, "$methodName: ${result.unsupportedPaths}")
        }
    }

    private fun replay(assertions: String) {
        assertNodeReplay(
            source = getResourcePath(sourcePath).readText() + "\n" + assertions,
            directory = directory,
            name = "computed-class-members",
            timeoutMessage = "Computed class member native replay timed out",
        )
    }
}
