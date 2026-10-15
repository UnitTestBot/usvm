package org.usvm.ts.calls

import org.jacodb.ets.model.EtsAssignStmt
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class CallsCyclicSourceTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `cyclic imported live binding sees writes and original replay uses one module instance`() {
        val fixture = fixture(
            source = """
                import { readCount, sameBox } from './helper';
                export let count = 0;
                export const box = { value: 10 };
                export const alias = box;
                export function liveCycle(value: number): boolean {
                  const before = readCount();
                  box.value = value;
                  count = before + 1;
                  if (before === 0 && readCount() === 1 && sameBox(value)) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "liveCycle",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "true",
            dependencies = mapOf(
                "helper.ts" to """
                    import { count, box, alias } from './Fixture';
                    export function readCount(): number { return count; }
                    export function sameBox(value: number): boolean { return box === alias && box.value === value; }
                """.trimIndent(),
            ),
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        fixture.assertReplayConfirmed(inputs)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `cyclic constant read before dependency evaluation remains unsupported`() {
        val fixture = fixture(
            source = """
                import { alias } from './helper';
                export const cache = [7];
                export function temporalCycle(value: number): boolean { return alias[0] === value; }
            """.trimIndent(),
            exportName = "temporalCycle",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return alias[0] === value;",
            dependencies = mapOf("helper.ts" to "import { cache } from './Fixture'; export const alias = cache;"),
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightReasonCode.TOP_LEVEL_INITIALIZATION_UNSUPPORTED, preflight.reasonCode)
    }

    @Test
    fun `startup retains initializer diagnostics and enters original constructor`() {
        val source = directory.resolve("StartupSubject.ts")
        Files.writeString(
            source,
            """
                import './environment';
                export class StartupSubject {
                  value: number;
                  constructor() { this.value = 1; }
                }
            """.trimIndent(),
        )

        Files.writeString(directory.resolve("environment.ts"), "const environmentSeed = Math.random();")

        val checked = loadCallsSourceProject(sourceRoot = directory, source = source)
        val startup = loadCallsStartupSourceProject(sourceRoot = directory, source = source)

        assertIs<CallsSourceProject.Unsupported>(checked)
        val loaded = assertIs<CallsSourceProject.Loaded>(startup)
        assertEquals(expected = 1, actual = loaded.initializationIssues.size)
        val targetClass = loaded.scene.projectClasses.single { it.name == "StartupSubject" }
        val method = targetClass.methods.single { it.name == "constructor" }
        var originalAssignments = 0
        val observer = object : TsInterpreterObserver {
            override fun onAssignmentCompleted(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsAssignStmt,
                scope: TsStepScope,
            ) {
                if (stmt.location.method.signature == method.signature) originalAssignments++
            }
        }
        val result = TsMachine(
            scene = loaded.scene,
            options = UMachineOptions(solverType = SolverType.YICES, timeout = 5.seconds),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { it.analyzeWithMetadata(methods = listOf(method)) }

        assertFalse(result.engineFailed)
        assertTrue(originalAssignments > 0)
        assertTrue(result.states.isNotEmpty())
    }
}
