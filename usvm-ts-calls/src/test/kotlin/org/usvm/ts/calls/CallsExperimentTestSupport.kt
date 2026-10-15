package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path

internal object CallsExperimentTestSupport {
    const val FIXTURE_TOOL_REVISION = "0000000000000000000000000000000000000001"

    fun manifest(sourceRoot: String, seeds: List<Long>) = CallsExperimentManifest(
        schemaVersion = CallsExperimentManifest.SCHEMA_VERSION,
        experimentId = "fixture",
        toolRevision = FIXTURE_TOOL_REVISION,
        nativeFrontendRevision = "bundled:test",
        solver = "YICES",
        searchPolicy = CALLS_PATH_SELECTION_STRATEGY.name,
        modelSet = CallsModelSetIdentity(
            ids = setOf("ts.array.pop", "ts.array.shift"),
            toolRevision = FIXTURE_TOOL_REVISION,
        ),
        seeds = seeds,
        perTargetBudgetMillis = 1_000L,
        projects = listOf(
            CallsProjectCase(
                projectId = "fixture/project",
                revision = "project-revision",
                sourceRoot = sourceRoot,
                development = true,
                functions = listOf(
                    CallsFunctionCase(
                        functionId = "fixture.ts::predicate/1",
                        sourceFile = "fixture.ts",
                        entryPoint = TypeScriptEntryPoint(
                            module = "fixture.ts",
                            exportName = "predicate",
                        ),
                        inputs = listOf(PropertyInput(name = "value", domain = BooleanDomain)),
                        targets = listOf(
                            CallsSourceTarget(
                                targetId = "fixture.ts::predicate/1#return",
                                siteId = "fixture.ts:1:1-1:12::predicate/1",
                                sourcePath = "fixture.ts",
                                startOffset = 0,
                                endOffset = 11,
                                start = CallsSourcePosition(line = 0, column = 0),
                                end = CallsSourcePosition(line = 0, column = 11),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    fun result(
        status: CallsSymbolicStatus,
        solverReached: Boolean = status == CallsSymbolicStatus.REACHED,
        inputs: List<JsConcreteValue>? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        solverReached = solverReached,
        inputs = inputs,
        elapsedMillis = 7L,
    )

    fun readRecords(path: Path): List<CallsRawRecord> = Files.readAllLines(path)
        .filter(String::isNotBlank)
        .map { line -> CallsExperimentJson.json.decodeFromString<CallsRawRecord>(line) }

    fun writeRecords(path: Path, records: List<CallsRawRecord>) {
        Files.writeString(
            path,
            records.joinToString(separator = "\n", postfix = "\n") { record ->
                CallsExperimentJson.json.encodeToString<CallsRawRecord>(record)
            },
        )
    }

    fun targetResult(inputs: List<JsConcreteValue>) = CallsTargetResult(
        experimentId = "fixture",
        projectId = "fixture/project",
        revision = "project-revision",
        development = true,
        functionId = "fixture.ts::predicate/1",
        targetId = "fixture.ts::predicate/1#return",
        siteId = "fixture.ts:1:1-1:12::predicate/1",
        profile = CallsExperimentProfile.FROZEN_STOP,
        seed = 1L,
        symbolicStatus = CallsSymbolicStatus.REACHED,
        solverReached = true,
        inputExtracted = true,
        inputs = inputs,
        replayStatus = CallsReplayStatus.CONFIRMED,
        symbolicElapsedMillis = 7L,
    )
}
