package org.usvm.ts.pbt.capability

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.external.ArtifactSourceRange
import org.usvm.ts.pbt.external.SourceCallableOrigin
import org.usvm.ts.pbt.external.SourceTargetRecord
import org.usvm.ts.pbt.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

class CapabilityManualSampleTest {
    @Test
    fun `frozen 50 target sample clears unsupported-prefix precision and recall gate`() {
        val samples = getResourcePath("/capability/manual-unsupported-prefix-sample-v1.jsonl")
            .readLines()
            .filter(String::isNotBlank)
            .map { Json.decodeFromString<SampleRecord>(it) }
        val denominator = broadDenominator().readLines()
            .filter(String::isNotBlank)
            .map { it.substringAfter('\t') }
            .toSet()

        assertEquals(50, samples.size)
        assertEquals(50, samples.map(SampleRecord::branchId).distinct().size)
        assertEquals(3, samples.map(SampleRecord::projectId).distinct().size)
        assertTrue(samples.all { it.branchId in denominator }, "sample contains an edge outside frozen D_broad-v1")

        val targets = samples.map(SampleRecord::target)
        val prefixes = samples.zip(targets).map { (sample, target) -> sample.prefix(target) }
        val report = CapabilityScanner.scan(targets, prefixes)
        val metrics = ManualCapabilityAudit.evaluate(
            report,
            samples.map { sample ->
                ManualUnsupportedPrefixAnnotation(
                    methodId = sample.methodId,
                    branchId = sample.branchId,
                    expectedUnsupported = sample.expectedUnsupported,
                    reviewEvidence = sample.reviewEvidence,
                )
            },
        )

        assertEquals(22, metrics.truePositive)
        assertEquals(0, metrics.falsePositive)
        assertEquals(28, metrics.trueNegative)
        assertEquals(0, metrics.falseNegative)
        assertTrue(metrics.precision >= 0.95, "precision=${metrics.precision}")
        assertTrue(metrics.recall >= 0.95, "recall=${metrics.recall}")
        assertEquals(22, report.statusCounts.getValue(CapabilityStatus.UNSUPPORTED))
        assertEquals(10, report.statusCounts.getValue(CapabilityStatus.SUPPORTED_WITH_FLAG))
        assertEquals(2, report.statusCounts.getValue(CapabilityStatus.NEEDS_DYNAMIC_PROBE))
    }

    @Serializable
    private data class SampleRecord(
        val projectId: String,
        val branchId: String,
        val fact: String,
        val expectedUnsupported: Boolean,
        val reviewEvidence: String,
    ) {
        val methodId: String get() = branchId.substringBeforeLast('#')

        fun target(): SourceTargetRecord {
            val match = requireNotNull(BRANCH_SUFFIX.find(branchId)) { "invalid frozen branch ID '$branchId'" }
            val (stmtIndex, successorOrdinal, successorStmtIndex) = match.destructured
            return SourceTargetRecord(
                methodId = methodId,
                branchId = branchId,
                stmtIndex = stmtIndex.toInt(),
                successorStmtIndex = successorStmtIndex.toInt(),
                successorOrdinal = successorOrdinal.toInt(),
                tsSourceRange = ArtifactSourceRange("manual/$projectId.ts", 0, 1, 0, 0, 0, 1),
                sourceOrigin = SourceCallableOrigin("manual/$projectId.ts", methodId, "free"),
                mappingStatus = "exact",
            )
        }

        fun prefix(target: SourceTargetRecord): CapabilityPrefixSlice {
            val factRecord = CapabilityAstFact(fact, "manual:$reviewEvidence")
            return CapabilityPrefixSlice(
                methodId = target.methodId,
                branchId = target.branchId,
                targetStmtIndex = target.stmtIndex,
                conservativeStmtIndices = listOf(0, target.stmtIndex).distinct().sorted(),
                mandatoryStmtIndices = listOf(0, target.stmtIndex).distinct().sorted(),
                mandatoryFacts = listOf(factRecord),
                facts = listOf(factRecord),
                complete = true,
            )
        }
    }

    private fun broadDenominator(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        return listOf(
            workingDirectory.resolve("usvm-ts-pbt/benchmarks/baselines/2026-07-19/denominators/D_broad-v1.edges.tsv"),
            workingDirectory.resolve("benchmarks/baselines/2026-07-19/denominators/D_broad-v1.edges.tsv"),
        ).firstOrNull(Path::exists) ?: error("frozen broad denominator is not reachable from $workingDirectory")
    }

    private companion object {
        val BRANCH_SUFFIX = Regex("#s(\\d+):(\\d+)->(\\d+)$")
    }
}
