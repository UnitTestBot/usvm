package org.usvm.ts.calls

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUndefinedType
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.FastCheckRuntime
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallsClassStartSmokeTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `class manifest fork threshold reaches the machine and result telemetry`() {
        val result = runFixture(
            sourceText = "export class Target { branch(flag: boolean): number { if (flag) return 1; return 2; } }",
            exploratory = true,
            forkLimit = 1UL,
        )

        assertEquals(expected = "FORK_LIMIT", actual = result.stopReason)
        assertEquals(expected = 1UL, actual = result.forkLimit)
        assertEquals(expected = 1UL, actual = result.generatedForks)
        assertFalse(result.engineFailed, result.toString())
        assertTrue(requireNotNull(result.rawAnalysis).remainingStates > 0)
    }

    @Test
    fun `synthetic default return and foreign class do not prove original target execution`() {
        val target = EtsClassSignature(
            name = "Target",
            file = EtsFileSignature(projectName = "fixture", fileName = "Target.ts")
        )
        val method = EtsMethodImpl(
            EtsMethodSignature(
                enclosingClass = target,
                name = "constructor",
                parameters = emptyList(),
                returnType = EtsUndefinedType,
            )
        )
        val location = EtsStmtLocation(method = method, index = 0)
        val synthetic = EtsReturnStmt(location = location, returnValue = null)
        val source = "return;"

        assertFalse(isOriginalClassStartupStep(synthetic, target, sourceFile = "Target.ts", sourceText = source))
        location.origin = EtsSourceSpan(
            fileName = "Target.ts", startOffset = 0, endOffset = source.length,
            startLine = 0, startColumn = 0, endLine = 0, endColumn = source.length, nodeKind = "Constructor",
        )
        assertFalse(isOriginalClassStartupStep(synthetic, target, sourceFile = "Target.ts", sourceText = source))
        location.origin = location.origin?.copy(nodeKind = "ReturnStatement")
        assertTrue(isOriginalClassStartupStep(synthetic, target, sourceFile = "Target.ts", sourceText = source))
        assertFalse(
            isOriginalClassStartupStep(
                synthetic,
                target.copy(name = "Foreign"),
                sourceFile = "Target.ts",
                sourceText = source
            )
        )
        assertFalse(
            isOriginalClassStartupStep(EtsNopStmt(location), target, sourceFile = "Target.ts", sourceText = source)
        )
    }

    @Test
    fun `uninitialized property and function type arrow are not source initializer assignments`() {
        assertFalse(hasStartupFieldInitializer("handler: (value: number) => number;"))
        assertFalse(hasStartupFieldInitializer("value: '=';"))
        assertFalse(hasStartupFieldInitializer("values: { '=': number };"))
        assertFalse(hasStartupFieldInitializer("value /* = */: number;"))
        assertFalse(hasStartupFieldInitializer("value: number; // = 1"))
        assertFalse(hasStartupFieldInitializer("value /*=> */: (x: number) => number;"))
        assertTrue(hasStartupFieldInitializer("handler = (value: number) => value + 1;"))
        assertTrue(hasStartupFieldInitializer("value: number = 1;"))
        assertTrue(hasStartupFieldInitializer("values: Map<string,number>=new Map();"))
    }

    @Test
    fun `input drift and unknown acquisition cannot establish complete provenance`() {
        val source = directory.resolve("Target.ts")
        Files.writeString(source, "export class Target { value = 1; }")
        val case = CallsClassStartCase(
            classId = "target",
            identity = "fixture@Target",
            projectRevision = "a".repeat(40),
            sourceRoot = directory.toString(),
            sourceFile = "Target.ts",
            className = "Target",
            inputSha256 = mapOf("Target.ts" to callsArtifactSha256(Files.readAllBytes(source))),
        )

        assertEquals(directory.toRealPath(), case.verifyInputs())
        assertFalse(case.acquisitionComplete)
        assertFailsWith<IllegalArgumentException> { case.copy(acquisitionComplete = true) }

        Files.writeString(source, "export class Target { value = 2; }")

        assertFailsWith<IllegalArgumentException> { case.verifyInputs() }
    }

    @Test
    fun `raw full class runs with original source steps without observation domain admission`() {
        val result = runFixture(
            sourceText = "export class Target { value = 1; read(): number { return this.value; } }",
        )

        assertTrue(result.started, result.toString())
        assertTrue(result.initialStates > 0)
        assertTrue(result.originalTargetStatementsEntered > 0)
        assertTrue(result.originalTargetClassSteps > 0)
        assertFalse(result.qualifyingStarted)
        assertTrue(result.methodSignatures.any { it.contains("read") })
    }

    @Test
    fun `first unsupported value proves source interpreter entry without a successful transition`() {
        val result = runFixture(
            sourceText = "declare function externalOperation(): number; " +
                "export class Target { constructor() { externalOperation(); } }",
            profile = CallsExperimentProfile.EMPTY_STOP,
        )

        assertTrue(result.started, result.toString())
        assertTrue(result.targetStatementEntered)
        assertTrue(result.originalTargetStatementsEntered > 0)
        assertEquals(0L, result.targetSuccessfulTransitions)
        assertEquals("STARTED_WITH_LIMITATION", result.startupStatus)
        assertTrue(result.runtimeLimited)
        assertFalse(result.qualifyingStarted)
    }

    @Test
    fun `first residual dispatcher call proves source interpreter entry`() {
        val result = runFixture(
            sourceText = "export class Target { constructor() { Date.now(); } }",
            profile = CallsExperimentProfile.EMPTY_STOP,
        )

        assertTrue(result.started, result.toString())
        assertTrue(result.targetStatementEntered)
        assertTrue(result.originalTargetStatementsEntered > 0)
        assertTrue(result.unknownCalls.isNotEmpty(), result.toString())
        assertEquals("STARTED_WITH_LIMITATION", result.startupStatus)
        assertTrue(result.unsupportedCall)
    }

    @Test
    fun `exploratory probe retains residual prefixes and independent source mapped IR universe`() {
        val result = runFixture(
            sourceText = "export class Target { constructor() { Date.now(); } }",
            profile = CallsExperimentProfile.EMPTY_STOP,
            exploratory = true,
            retainRawStateDiagnostics = true,
        )

        assertTrue(result.started, result.toString())
        val raw = checkNotNull(result.rawAnalysis)
        assertEquals(30_000L, raw.budgetMillis)
        assertEquals(null, raw.collectedStatesLimit)
        assertTrue(raw.stoppedPrefixStates > 0)
        assertTrue(checkNotNull(raw.stateSnapshots).records >= raw.stoppedPrefixStates)
        assertTrue(checkNotNull(raw.stateSnapshots).complete)
        assertTrue(checkNotNull(raw.symbolicExpressions).complete)
        assertTrue(raw.sourceMappedIrUniverse.isNotEmpty())
        assertTrue(raw.sourceMappedIrEntered.isNotEmpty())
        assertTrue(raw.sourceMappedIrUniverse.containsAll(raw.sourceMappedIrEntered))
        assertTrue(raw.stateSnapshotFailures.isEmpty(), raw.stateSnapshotFailures.toString())
        assertTrue(Files.isRegularFile(directory.resolve("output/states.jsonl.gz")))
        assertEquals(2, raw.timingSchemaVersion)
        assertTrue(raw.searchElapsedMillis > 0)
        assertEquals(raw.analysisElapsedMillis, raw.analysisSetupElapsedMillis + raw.searchElapsedMillis)
        assertEquals((raw.searchElapsedMillis - raw.budgetMillis).coerceAtLeast(0), raw.budgetOverrunMillis)
        assertTrue(raw.searchSnapshotElapsedNanos > 0)
        assertEquals(0L, raw.drainSnapshotElapsedNanos)
        assertEquals(0L, raw.remainingStates)
    }

    @Test
    fun `symbolic probe saves concrete candidates without raw state journals by default`() {
        val result = runFixture(
            sourceText = "export class Target { read(value: number): number { return value > 0 ? 1 : -1; } }",
            exploratory = true,
        )

        val analysis = checkNotNull(result.rawAnalysis)
        assertFalse(analysis.rawStateDiagnosticsEnabled)
        assertEquals(null, analysis.stateSnapshots)
        assertEquals(null, analysis.symbolicExpressions)
        assertTrue(checkNotNull(analysis.inputCandidates).records > 0)
        assertTrue(checkNotNull(analysis.inputCandidates).complete)
        assertTrue(checkNotNull(analysis.inputExtractionFailures).complete)
        assertTrue(analysis.candidateExtractionElapsedNanos > 0)
        assertEquals(0L, analysis.searchSnapshotElapsedNanos)
        assertFalse(Files.exists(directory.resolve("output/states.jsonl.gz")))
        assertFalse(Files.exists(directory.resolve("output/expressions.jsonl.gz")))
    }

    @Test
    fun `generic entry inputs use initialized number boolean reference and default sorts`() {
        val source = """
            export class Target {
                numeric<T extends number>(value: T): number { return value > 0 ? 1 : 0; }
                logical<T extends boolean>(value: T): number { return value ? 1 : 0; }
                reference<T extends string>(value: T): number { return value.length; }
                defaulted<T = number>(value: T): boolean { return typeof value === 'number'; }
            }
        """.trimIndent()

        val result = runFixture(sourceText = source, exploratory = true)
        val metadata = checkNotNull(result.rawAnalysis?.inputCandidates)
        val candidates = Files.readAllLines(directory.resolve("output").resolve(metadata.path)).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }.groupBy { it.methodName }

        assertTrue(result.started, result.toString())
        assertFalse(result.engineFailed, result.toString())
        fun values(method: String): List<JsConcreteValue?> = checkNotNull(candidates[method]).map {
            it.inputs.arguments.single().primitive
        }
        assertTrue(values("numeric").all { it is JsConcreteValue.Number })
        assertTrue(values("logical").all { it is JsConcreteValue.Boolean })
        assertTrue(values("reference").all { it is JsConcreteValue.String })
        val failures = Files.readAllLines(directory.resolve("output/input-extraction-failures.jsonl"))
        assertTrue(values("defaulted").isNotEmpty())
        assertTrue(values("defaulted").any { it !is JsConcreteValue.Number })
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test
    fun `saved stopped candidate restores original receiver and arguments in native execution`() {
        val source = """
            declare class External { static stop(): number; }
            export class Box { value: number = 0; }
            export class Target {
                value: number = 0;
                run(box: Box, items: number[]): number {
                    if (this.value !== 11 || box.value !== 12 || items.length !== 1 || items[0] !== 13) return 0;
                    this.value = 91;
                    box.value = 92;
                    items[0] = 93;
                    return this.nested(items, box);
                }
                nested(items: number[], box: Box): number { return External.stop(); }
            }
        """.trimIndent()

        val result = runFixture(sourceText = source, exploratory = true, profile = CallsExperimentProfile.EMPTY_STOP)
        val metadata = checkNotNull(result.rawAnalysis?.inputCandidates)
        val candidates = Files.readAllLines(directory.resolve("output").resolve(metadata.path)).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }
        val candidate = candidates.single { it.methodName == "run" && it.outcome.startsWith("STOPPED_PREFIX") }
        Files.writeString(directory.resolve("candidate.json"), encodeCallsArtifact(candidate))

        assertEquals(CallsCoverageCandidateOrigin.SUSPENDED, candidate.origin)
        assertEquals(null, candidate.completion)
        assertFalse(candidate.suspendedAtMachineStop)
        replaySavedStoppedCandidate()
    }

    private fun replaySavedStoppedCandidate() {
        val decoder = FastCheckRuntime.protocolJsonModule().resolveSibling("js-value.js").toUri().toString()
        val replaySource = """
            import { readFileSync } from 'node:fs';
            import { Target, Box } from './Target.ts';
            import { decodeJsValue } from '$decoder';
            ${callsConcreteHeapRestoreSource()}
            export function checkCandidate(_sentinel: boolean): boolean {
              const candidate = JSON.parse(readFileSync(new URL('./candidate.json', import.meta.url), 'utf8'));
              const prototypes = new Map<string, object>();
              for (const node of candidate.inputs.objects) {
                if (node.className === 'Target') prototypes.set(node.classSignature, Target.prototype);
                if (node.className === 'Box') prototypes.set(node.classSignature, Box.prototype);
              }
              const restored = restoreCallsConcreteHeap(candidate.inputs, prototypes, decodeJsValue);
              try { restored.receiver.run(...restored.arguments); } catch (error) {
                if (!(error instanceof ReferenceError)) throw error;
              }
              return restored.receiver.value === 91 && restored.arguments[0].value === 92 &&
                     restored.arguments[1][0] === 93;
            }
        """.trimIndent()
        Files.writeString(directory.resolve("replay.mts"), replaySource)
        val property = PropertyDefinition(
            id = PropertyId("calls.saved-stopped-inputs"),
            inputs = listOf(PropertyInput(name = "sentinel", domain = BooleanDomain)),
            predicate = TypeScriptEntryPoint(module = "replay.mts", exportName = "checkCandidate"),
        )

        val result = FastCheckBackend(sourceRoots = listOf(directory)).run(
            property = property,
            configuration = PropertyRunConfiguration(seed = 17, numRuns = 1, timeoutMillis = 20_000),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status, result.toString())
    }

    @Test
    fun `merged interface does not make its runtime class ambiguous`() {
        val result = runFixture(
            sourceText = "export class Target { value = 1; read(): number { return this.value; } } " +
                "export interface Target { read(): number; }",
        )

        assertEquals(2, result.targetDeclarationCandidates.size)
        assertTrue(result.targetDeclarationCandidates.any { it.contains("[INTERFACE]") })
        assertTrue(result.targetDeclarationCandidates.any { it.contains("[CLASS]") })
        assertTrue(result.methodSignatures.any { it.contains("read") })
        assertTrue(result.targetSignature != null)
        assertTrue(result.stage != "TARGET", result.toString())
    }

    @Test
    fun `interface before runtime class keeps lifted closures in the frozen IR universe`() {
        assertMergedClosureUniverse(interfaceFirst = true)
    }

    @Test
    fun `interface after runtime class keeps lifted closures in the frozen IR universe`() {
        assertMergedClosureUniverse(interfaceFirst = false)
    }

    private fun assertMergedClosureUniverse(interfaceFirst: Boolean) {
        val augmentation = "export interface Target { read(value: number): number; }"
        val implementation = "export class Target { read(value: number): number { " +
            "const increment = (input: number): number => { return input + 1; }; return increment(value); } }"
        val source = if (interfaceFirst) augmentation + implementation else implementation + augmentation

        val result = runFixture(sourceText = source, exploratory = true)

        assertTrue(result.started, result.toString())
        val raw = checkNotNull(result.rawAnalysis)
        val closurePoints = raw.sourceMappedIrUniverse.filter { it.method.contains("%AM") }
        assertTrue(closurePoints.isNotEmpty(), raw.toString())
        assertTrue(raw.sourceMappedIrEntered.any { it in closurePoints }, raw.toString())
        assertTrue(raw.sourceMappedIrUniverse.containsAll(raw.sourceMappedIrEntered), raw.toString())
        assertTrue(raw.returnedStates > 0, raw.toString())
    }

    @Test
    fun `lowered closures keep complete IR but require their owning member environment`() {
        val result = runFixture(
            sourceText = "export class Target { value = 1; read(): number { " +
                "const callback = () => this.value; return callback(); } }",
        )

        assertTrue(result.started, result.toString())
        val dependent = result.entryPointEligibility.filter { !it.standalone }
        assertTrue(dependent.isNotEmpty(), result.toString())
        assertTrue(dependent.all { it.reason == "LOWERED_CLOSURE_REACHABLE_VIA_OWNER" })
        assertTrue(result.methodSignatures.containsAll(dependent.map { it.methodSignature }))
        assertTrue(result.entryPointEligibility.any { it.standalone && it.methodSignature.contains("read") })
        assertTrue(result.generatedInitialMethodCount < result.methodSignatures.size)
    }

    @Test
    fun `generic interpreter entry callback observes source property definitions`() {
        val result = runFixture(
            sourceText = "export class Target { read(): number { const object = { value: 1 }; return object.value; } }",
            exploratory = true,
        )

        assertTrue(result.started, result.toString())
        val raw = checkNotNull(result.rawAnalysis)
        assertTrue(raw.sourceEnteredStatementKinds.getOrDefault("EtsDefineDataPropertyStmt", 0) > 0, raw.toString())
    }

    private fun runFixture(
        sourceText: String,
        profile: CallsExperimentProfile = CallsExperimentProfile.FROZEN_STOP,
        exploratory: Boolean = false,
        retainRawStateDiagnostics: Boolean = false,
        forkLimit: ULong? = null,
    ): CallsClassStartResult {
        val source = directory.resolve("Target.ts")
        Files.writeString(source, sourceText)
        val config = directory.resolve("tsconfig.json")
        Files.writeString(config, """{"compilerOptions":{"target":"ES2022","types":[]},"files":["Target.ts"]}""")
        val inputs = listOf(source, config).associate {
            it.fileName.toString() to callsArtifactSha256(Files.readAllBytes(it))
        }
        val case = CallsClassStartCase(
            classId = "target",
            identity = "fixture@Target",
            projectRevision = "a".repeat(40),
            sourceRoot = directory.toString(),
            sourceFile = "Target.ts",
            className = "Target",
            inputSha256 = inputs,
        )
        val manifest = CallsClassStartManifest(
            toolRevision = CallsBuildIdentity.toolRevision,
            nativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
            ledgerSha256 = "b".repeat(64),
            expectedClassCount = 1,
            profile = profile,
            budgetMillis = if (exploratory) 30_000 else 2_000,
            collectedStatesLimit = if (exploratory) null else 10,
            forkLimit = forkLimit,
            classes = listOf(case),
            retainRawStateDiagnostics = retainRawStateDiagnostics,
        )
        val path = directory.resolve("manifest.json")
        Files.writeString(path, encodeClassStartManifest(manifest))
        val output = directory.resolve("output")

        val args = listOf(path.toString(), "target", output.toString())
        if (exploratory) {
            runClassSymbolicProbe(args)
        } else {
            runClassStartSmoke(args)
        }

        return CallsExperimentJson.json.decodeFromString(Files.readString(output.resolve("result.json")))
    }
}
