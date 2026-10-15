package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayShiftReplayTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `compatibility array growth rejects capacity overflow`() {
        val source = directory.resolve("ArrayCompatibilityCapacity.ts")
        source.writeText(
            """
                class ArrayCompatibilityCapacity {
                    pushBelow() {
                        const values = [1];
                        const alias = values;
                        const length = values.push(3);
                        return length === 2 && alias[0] === 1 && alias[1] === 3 ? 1 : -1;
                    }

                    unshiftBelow() {
                        const values = [1];
                        const alias = values;
                        const length = values.unshift(3);
                        return length === 2 && alias[0] === 3 && alias[1] === 1 ? 1 : -1;
                    }

                    pushAtLimit() {
                        const values = [1, 2];
                        return values.push(3);
                    }

                    unshiftAtLimit() {
                        const values = [1, 2];
                        return values.unshift(3);
                    }

                    concatBelow() {
                        const values = [1];
                        const alias = values;

                        const combined = values.concat([3]);

                        return combined[0] === 1 && combined[1] === 3 &&
                            alias.length === 1 && alias[0] === 1 ? 1 : -1;
                    }

                    concatAtLimit() {
                        const values = [1, 2];
                        return values.concat([3]);
                    }
                }
            """.trimIndent()
        )
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(file))
        val methods = scene.projectClasses
            .single { it.name == "ArrayCompatibilityCapacity" }
            .methods
            .associateBy { it.name }

        for (operation in listOf("push", "unshift", "concat")) {
            val events = mutableListOf<TsRuntimeFeatureLimitationEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                    events += event
                }
            }
            val below = methods.getValue("${operation}Below")
            val atLimit = methods.getValue("${operation}AtLimit")

            val belowAnalysis = TsMachine(
                scene = scene,
                options = machineOptions,
                tsOptions = TsOptions(maxArraySize = 2),
                observer = observer,
                unknownCallDispatcher = TsCompatibilityUnknownCallDispatcher,
            ).use { machine -> machine.analyzeWithMetadata(methods = listOf(below)) }
            val belowReturns = belowAnalysis.states.map { state ->
                assertIs<TsTestValue.TsNumber>(TsTestResolver().resolve(below, state).returnValue).number
            }

            assertEquals(listOf(1.0), belowReturns)
            assertTrue(!belowAnalysis.engineFailed)
            assertTrue(events.isEmpty())

            val atLimitAnalysis = TsMachine(
                scene = scene,
                options = machineOptions,
                tsOptions = TsOptions(maxArraySize = 2),
                observer = observer,
                unknownCallDispatcher = TsCompatibilityUnknownCallDispatcher,
            ).use { machine -> machine.analyzeWithMetadata(methods = listOf(atLimit)) }

            assertTrue(atLimitAnalysis.states.isEmpty())
            assertTrue(!atLimitAnalysis.engineFailed)
            assertEquals(listOf(TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY), events.map { it.reason })
        }
    }

    @TestFactory
    fun `symbolic shift inputs results and heap changes replay in JavaScript`(): List<DynamicTest> {
        val cases = arrayShiftReplayCases()
        val source = directory.resolve("ArrayShiftReplay.ts")
        source.writeText(renderSource(cases))
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(file))
        val methods = scene.projectClasses
            .single { it.name == "ArrayShiftReplay" }
            .methods
            .associateBy { it.name }

        return cases.mapIndexed { index, case ->
            DynamicTest.dynamicTest(case.name) {
                val method = methods.getValue("case$index")

                val dispatcher = TsCompatibilityUnknownCallDispatcher.takeIf { case.compatibility }
                val tests = TsMachine(
                    scene = scene,
                    options = machineOptions,
                    tsOptions = TsOptions(eraseTypeAssertions = true),
                    unknownCallDispatcher = dispatcher,
                ).use { machine ->
                    machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
                }

                assertTrue(tests.isNotEmpty())
                val results = tests.map {
                    assertIs<TsTestValue.TsNumber>(it.returnValue, message = it.toString()).number
                }.toSet()
                assertEquals((0..case.maxResult).map { it.toDouble() }.toSet(), results)

                val script = buildString {
                    appendLine(renderSource(cases))
                    appendLine(JS_SAME_VALUE)
                    tests.forEachIndexed { stateIndex, test ->
                        val arguments = test.before.parameters.joinToString(transform = ::jsValue)
                        val expectedAfter = test.after.parameters.joinToString(transform = ::jsValue)
                        appendLine("{")
                        appendLine("const args = [$arguments];")
                        appendLine("const actual = new ArrayShiftReplay().case$index(...args);")
                        val expectedResult = jsValue(test.returnValue)
                        appendLine("if (!same(actual, $expectedResult)) throw Error('result $stateIndex');")
                        appendLine("if (!same(args, [$expectedAfter])) throw Error('heap $stateIndex');")
                        appendLine("}")
                    }
                }
                assertNodeReplay(
                    source = script,
                    directory = directory,
                    name = "replay$index",
                    timeoutMessage = "Replay timed out",
                )
            }
        }
    }

    private fun renderSource(cases: List<ArrayShiftReplayCase>): String = buildString {
        appendLine("class ReplayElement {}")
        appendLine("class ArrayShiftReplay {")
        cases.forEachIndexed { index, case ->
            appendLine("case$index(${case.parameters}) { ${case.body} }")
        }
        appendLine("}")
    }

    private fun jsValue(value: TsTestValue): String = when (value) {
        TsTestValue.TsUndefined -> "undefined"
        TsTestValue.TsNull -> "null"
        is TsTestValue.TsBoolean -> value.value.toString()
        is TsTestValue.TsNumber -> value.number.toString()
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsArray<*> -> value.values.joinToString(prefix = "[", postfix = "]", transform = ::jsValue)
        is TsTestValue.TsClass -> value.properties.entries.joinToString(prefix = "({", postfix = "})") {
            "${jsString(it.key)}: ${jsValue(it.value)}"
        }
        else -> error("Unsupported replay value: $value")
    }

    private companion object {
        const val JS_SAME_VALUE = """
            function same(a, b) {
                if (Object.is(a, b)) return true;
                if (!a || !b || typeof a !== 'object' || typeof b !== 'object') return false;
                if (Array.isArray(a) !== Array.isArray(b)) return false;
                const keys = Object.keys(a);
                return keys.length === Object.keys(b).length && keys.every(k => same(a[k], b[k]));
            }
        """

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
