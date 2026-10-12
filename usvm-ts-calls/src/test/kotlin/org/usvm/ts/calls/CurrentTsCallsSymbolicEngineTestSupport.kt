package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

internal open class CurrentTsCallsSymbolicEngineTestSupport {
    @TempDir
    lateinit var directory: Path

    protected fun fixture(
        source: String,
        exportName: String,
        inputs: List<PropertyInput>,
        targetStatement: String,
        targetMode: CallsSourceTargetMode = CallsSourceTargetMode.ENTRY,
        returnExpression: String? = null,
    ): SymbolicFixture {
        val sourceRoot = Files.createDirectory(directory.resolve(exportName))
        val sourceFile = sourceRoot.resolve("Fixture.ts")
        Files.writeString(sourceFile, source)
        runGit(sourceRoot, "init")
        runGit(sourceRoot, "config", "user.name", "USVM Tests")
        runGit(sourceRoot, "config", "user.email", "usvm@example.test")
        runGit(sourceRoot, "add", "Fixture.ts")
        runGit(sourceRoot, "commit", "-m", "fixture")
        val revision = runGit(sourceRoot, "rev-parse", "HEAD").trim()
        val targetStart = source.indexOf(targetStatement)
        check(targetStart >= 0) { "Missing target statement: $targetStatement" }
        val targetEnd = targetStart + targetStatement.length
        val returnExpressionStart = returnExpression?.let { expression ->
            source.indexOf(expression, startIndex = targetStart).takeIf { offset ->
                offset in targetStart until targetEnd
            }
                ?: error("Missing return expression inside target: $expression")
        }
        val target = CallsSourceTarget(
            targetId = "$exportName#$targetStatement",
            siteId = "$exportName:$targetStart:$targetEnd",
            sourcePath = "Fixture.ts",
            startOffset = targetStart,
            endOffset = targetEnd,
            start = sourcePositionAt(source = source, offset = targetStart),
            end = sourcePositionAt(source = source, offset = targetEnd),
            mode = targetMode,
            returnExpressionStartOffset = returnExpressionStart,
            returnExpressionEndOffset = returnExpressionStart?.plus(requireNotNull(returnExpression).length),
        )
        val function = CallsFunctionCase(
            functionId = exportName,
            sourceFile = "Fixture.ts",
            entryPoint = TypeScriptEntryPoint(module = "Fixture.ts", exportName = exportName),
            inputs = inputs,
            targets = listOf(target),
        )
        val project = CallsProjectCase(
            projectId = exportName,
            revision = revision,
            sourceRoot = ".",
            development = true,
            functions = listOf(function),
        )

        return SymbolicFixture(
            sourceRoot = sourceRoot,
            project = project,
            function = function,
            target = target,
        )
    }

    private fun runGit(directory: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-C", directory.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Git ${arguments.joinToString()} failed: $output" }
        return output
    }

    protected data class SymbolicFixture(
        val sourceRoot: Path,
        val project: CallsProjectCase,
        val function: CallsFunctionCase,
        val target: CallsSourceTarget,
    ) {
        private val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:test",
        )

        fun preflight(): CallsSymbolicPreflightResult = engine.preflight(
            CallsSymbolicPreflightRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                expectedNativeFrontendRevision = "bundled:test",
            )
        )

        fun search(
            modelIds: Set<String>,
            unknownCallEventSink: ((TsUnknownCallEvent) -> Unit)? = null,
            runtimeLimitationEventSink: ((TsRuntimeFeatureLimitationEvent) -> Unit)? = null,
            profile: CallsExperimentProfile = CallsExperimentProfile.FROZEN_STOP,
        ): CallsSymbolicSearchResult = engine.search(
            CallsSymbolicSearchRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                profile = profile,
                frozenModelIds = modelIds,
                expectedNativeFrontendRevision = "bundled:test",
                seed = 0,
                budget = 10.seconds,
                unknownCallEventSink = unknownCallEventSink,
                runtimeLimitationEventSink = runtimeLimitationEventSink,
            )
        )

        fun assertReplayConfirmed(inputs: List<JsConcreteValue>) {
            val replay = OriginalTypeScriptTargetReplayer().replay(
                sourceRoots = listOf(sourceRoot),
                entryPoint = function.entryPoint,
                inputs = inputs,
                target = target,
                timeoutMillis = 10_000L,
            )

            assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        }
    }
}
