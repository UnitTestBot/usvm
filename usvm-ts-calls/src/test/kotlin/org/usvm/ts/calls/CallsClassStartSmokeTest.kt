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
import org.usvm.ts.pbt.model.JsConcreteValue
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
                defaulted<T = number>(value: T): boolean {
                    if (typeof value === 'number') return true;
                    return false;
                }
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
        // Distinct return paths force both kinds; one unconstrained return permits an arbitrary solver witness.
        assertTrue(values("defaulted").any { it is JsConcreteValue.Number })
        assertTrue(values("defaulted").any { it !is JsConcreteValue.Number })
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test
    fun `saved stopped candidate replays original receiver aliases and static environment`() {
        val source = """
            export class Box { value: number = 0; }
            export class Target {
                static marker: number = 17;
                value: number = 0;
                run(box: Box, alias: Box, items: number[]): number {
                    if (this.value !== 11 || box.value !== 12 || box !== alias || items.length !== 1 || items[0] !== 13 ||
                        Target.marker !== 17) return 0;
                    this.value = 91;
                    box.value = 92;
                    items[0] = 93;
                    Target.marker = 97;
                    return this.nested(items, alias);
                }
                nested(items: number[], box: Box): number {
                    return Math.abs(this.value + box.value + items[0] + Target.marker);
                }
            }
        """.trimIndent()

        val result = runFixture(sourceText = source, exploratory = true, profile = CallsExperimentProfile.EMPTY_STOP)
        val metadata = checkNotNull(result.rawAnalysis?.inputCandidates)
        val candidates = Files.readAllLines(directory.resolve("output").resolve(metadata.path)).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }
        val candidate = candidates.single { it.methodName == "run" && it.outcome.startsWith("STOPPED_PREFIX") }

        assertEquals(CallsCoverageCandidateOrigin.SUSPENDED, candidate.origin)
        assertEquals(null, candidate.completion)
        assertFalse(candidate.suspendedAtMachineStop)
        assertTrue(candidate.environmentComplete, candidate.environmentLimitations.toString())
        assertFalse(candidate.nativeReplayValidated)
        val marker = candidate.inputs.environmentFields.single { it.name == "marker" }
        assertEquals(JsConcreteValue.number(17.0), marker.value.primitive)
        assertEquals(candidate.inputs.arguments[0], candidate.inputs.arguments[1])

        val replayPath = directory.resolve("native-input-replay.json")
        runSavedInputReplay(
            listOf(
                directory.resolve("manifest.json").toString(),
                "target",
                directory.resolve("output").toString(),
                candidate.stateId,
                "20000",
                replayPath.toString(),
            )
        )
        val replay = CallsExperimentJson.json.decodeFromString<CallsSavedInputReplayResult>(
            Files.readString(replayPath)
        )

        assertSavedInputReturn(replay = replay, expected = 373.0)

        val adjusted = candidate.copy(
            inputs = candidate.inputs.copy(
                environmentFields = listOf(
                    marker.copy(value = CallsConcreteHeapValue(primitive = JsConcreteValue.number(19.0)))
                ),
            )
        )
        assertSavedInputReturned(result = result, candidate = adjusted, expected = 0.0)
    }

    @Test
    fun `saved stopped candidate restores mutable module binding before native invocation`() {
        val source = """
            export let marker: number = 17;
            export class Target {
                run(value: number): number {
                    if (value !== 11) return 0;
                    marker = marker + 80;
                    return Math.abs(marker + value);
                }
            }
        """.trimIndent()

        val result = runFixture(sourceText = source, exploratory = true, profile = CallsExperimentProfile.EMPTY_STOP)
        val candidates = Files.readAllLines(directory.resolve("output/input-candidates.jsonl")).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }
        val candidate = candidates.single { it.methodName == "run" && it.outcome.startsWith("STOPPED_PREFIX") }
        val marker = candidate.inputs.environmentFields.single { it.name == "marker" }

        assertTrue(candidate.environmentComplete, candidate.environmentLimitations.toString())
        assertEquals(CallsConcreteEnvironmentFieldKind.MODULE_BINDING, marker.kind)
        assertEquals(JsConcreteValue.number(17.0), marker.value.primitive)
        assertSavedInputReturned(result = result, candidate = candidate, expected = 108.0)

        // A transport control distinguishes the setter from merely re-running the source initializer.
        val adjusted = candidate.copy(
            inputs = candidate.inputs.copy(
                environmentFields = listOf(
                    marker.copy(value = CallsConcreteHeapValue(primitive = JsConcreteValue.number(5.0)))
                ),
            )
        )
        assertSavedInputReturned(result = result, candidate = adjusted, expected = 96.0)

        val partial = replayCallsSavedInput(
            sourceRoot = directory,
            sourceFile = "Target.ts",
            targetSignature = checkNotNull(result.targetSignature),
            candidate = candidate.copy(
                environmentComplete = false,
                environmentLimitations = listOf("incomplete initializer")
            ),
            timeoutMillis = 20_000,
        )

        assertEquals(CallsSavedInputReplayStatus.UNSUPPORTED, partial.status)
        assertEquals(null, partial.observation)
        assertFalse(partial.nativeReplayValidated)

        val missingCallable = replayCallsSavedInput(
            sourceRoot = directory,
            sourceFile = "Target.ts",
            targetSignature = checkNotNull(result.targetSignature),
            candidate = candidate.copy(methodName = "missingCallable"),
            timeoutMillis = 20_000,
        )

        assertEquals(CallsSavedInputReplayStatus.HARNESS_FAILURE, missingCallable.status)
        assertFalse(missingCallable.nativeReplayValidated)
    }

    private fun assertSavedInputReturned(
        result: CallsClassStartResult,
        candidate: CallsClassInputCandidate,
        expected: Double,
    ) {
        val replay = replayCallsSavedInput(
            sourceRoot = directory,
            sourceFile = "Target.ts",
            targetSignature = checkNotNull(result.targetSignature),
            candidate = candidate,
            timeoutMillis = 20_000,
        )

        assertSavedInputReturn(replay = replay, expected = expected)
    }

    private fun assertSavedInputReturn(replay: CallsSavedInputReplayResult, expected: Double) {
        assertEquals(CallsSavedInputReplayStatus.INVOKED, replay.status, replay.toString())
        val observation = checkNotNull(replay.observation).observation
        assertEquals(JsConcreteValue.String("RETURNED"), observation.values["completion"])
        val returnJson = observation.values["returnValue"] as JsConcreteValue.String
        val returned = CallsExperimentJson.json.decodeFromString<JsConcreteValue>(returnJson.value)
        assertEquals(JsConcreteValue.number(expected), returned)
        assertFalse(replay.nativeReplayValidated)
        assertFalse(replay.nativeCoverageAvailable)
    }

    @Test
    fun `unsupported environment retains concrete original roots as explicitly partial`() {
        val source = """
            declare class External { static stop(): number; }
            export class Target {
                static items: number[] = new Array<number>(2);
                run(value: number): number {
                    if (value !== 11 || Target.items.length !== 2) return 0;
                    return External.stop();
                }
            }
        """.trimIndent()

        val result = runFixture(sourceText = source, exploratory = true, profile = CallsExperimentProfile.EMPTY_STOP)
        val metadata = checkNotNull(result.rawAnalysis?.inputCandidates)
        val candidates = Files.readAllLines(directory.resolve("output").resolve(metadata.path)).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }
        val candidate = candidates.single { it.methodName == "run" && it.outcome.startsWith("STOPPED_PREFIX") }
        val failureLines = Files.readAllLines(directory.resolve("output/input-extraction-failures.jsonl"))
        val failures = failureLines.map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputExtractionFailure>(it)
        }

        assertEquals(JsConcreteValue.number(11.0), candidate.inputs.arguments.single().primitive)
        assertFalse(candidate.environmentComplete)
        assertFalse(candidate.nativeReplayValidated)
        assertTrue(candidate.environmentLimitations.any { it.contains("sparse", ignoreCase = true) })
        assertTrue(candidate.inputs.environmentFields.isEmpty())
        assertTrue(failures.any { it.stateId == candidate.stateId && it.environmentOnly })
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

        val failures = Files.readAllLines(directory.resolve("output/input-extraction-failures.jsonl"))
        val candidates = Files.readAllLines(directory.resolve("output/input-candidates.jsonl")).map {
            CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it)
        }
        val memberCandidates = candidates.filter { it.methodName == "read" }

        assertTrue(failures.isEmpty(), failures.toString())
        assertTrue(memberCandidates.isNotEmpty())
        for (candidate in memberCandidates) {
            val receiverId = checkNotNull(candidate.inputs.receiver?.objectId)
            val receiver = candidate.inputs.objects.single { it.id == receiverId }
            assertEquals(result.targetSignature, receiver.classSignature)
        }
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
