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
        )

        assertTrue(result.started, result.toString())
        val raw = checkNotNull(result.rawAnalysis)
        assertEquals(30_000L, raw.budgetMillis)
        assertEquals(null, raw.collectedStatesLimit)
        assertTrue(raw.stoppedPrefixStates > 0)
        assertTrue(raw.stateSnapshots.records >= raw.stoppedPrefixStates)
        assertTrue(raw.stateSnapshots.complete)
        assertTrue(raw.symbolicExpressions.complete)
        assertTrue(raw.sourceMappedIrUniverse.isNotEmpty())
        assertTrue(raw.sourceMappedIrEntered.isNotEmpty())
        assertTrue(raw.sourceMappedIrUniverse.containsAll(raw.sourceMappedIrEntered))
        assertTrue(raw.stateSnapshotFailures.isEmpty(), raw.stateSnapshotFailures.toString())
        assertTrue(Files.isRegularFile(directory.resolve("output/states.jsonl.gz")))
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
