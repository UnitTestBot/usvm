package org.usvm.ts.pbt.capability

import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.lt
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.external.ArtifactContractCodec
import org.usvm.ts.pbt.external.ArtifactSourceRange
import org.usvm.ts.pbt.external.SourceCallableOrigin
import org.usvm.ts.pbt.external.SourceTargetRecord
import org.usvm.ts.pbt.external.TargetManifest
import org.usvm.ts.pbt.replay.DynamicProbeOutcome
import org.usvm.ts.pbt.replay.StaticCapabilityLabel
import java.nio.file.Path
import kotlin.io.path.writeText

class CapabilityScannerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `conservative prefix contains every feasible ancestor while mandatory prefix uses dominators`() {
        val cfg = CapabilityCfg(
            entryStmtIndex = 0,
            nodes = listOf(
                node(0, 1, 2, fact = CapabilityLabel.PRIMITIVE_ARITHMETIC),
                node(1, 3, fact = CapabilityLabel.CALLABLE),
                node(2, 3, fact = CapabilityLabel.ARRAY_OBJECT),
                node(3, 4, 5),
                node(4, 3, fact = CapabilityLabel.ITERATOR),
                node(5),
            ),
        )

        val prefix = CapabilityPrefixSlicer.slice("m", "b", 3, cfg)

        assertTrue(prefix.complete)
        assertEquals(listOf(0, 1, 2, 3, 4), prefix.conservativeStmtIndices)
        assertEquals(listOf(0, 3), prefix.mandatoryStmtIndices)
        assertEquals(
            setOf(
                CapabilityLabel.PRIMITIVE_ARITHMETIC,
                CapabilityLabel.CALLABLE,
                CapabilityLabel.ARRAY_OBJECT,
                CapabilityLabel.ITERATOR,
            ),
            prefix.facts.map(CapabilityAstFact::kind).toSet(),
        )
    }

    @Test
    fun `broken CFG is an incomplete prefix instead of unsupported`() {
        val prefix = CapabilityPrefixSlicer.slice(
            methodId = "m",
            branchId = "b",
            targetStmtIndex = 1,
            cfg = CapabilityCfg(0, listOf(node(0, 99), node(1, fact = CapabilityLabel.ITERATOR))),
        )

        assertFalse(prefix.complete)
        assertEquals("dangling_cfg_successor", prefix.uncertaintyReason)
        val record = StaticCapabilityClassifier.classify(target("m", "b", mapping = "exact"), prefix)
        // A broken graph cannot prove that the target fact is mandatory.
        assertEquals(CapabilityStatus.NEEDS_DYNAMIC_PROBE, record.staticStatus)
        assertEquals(CapabilityLabel.ITERATOR, record.primaryLabel)
    }

    @Test
    fun `closed taxonomy produces all five statuses and never coerces uncertainty to unsupported`() {
        val cases = listOf(
            Triple(CapabilityLabel.PRIMITIVE_ARITHMETIC, "exact", CapabilityStatus.SUPPORTED),
            Triple(CapabilityLabel.CALLABLE, "exact", CapabilityStatus.SUPPORTED_WITH_FLAG),
            Triple(CapabilityLabel.UNRESOLVED_POINTER_CALL, "exact", CapabilityStatus.EXTERNAL_ONLY),
            Triple(CapabilityLabel.SPREAD_YIELD, "exact", CapabilityStatus.UNSUPPORTED),
            Triple(CapabilityLabel.PRIMITIVE_ARITHMETIC, "ambiguous", CapabilityStatus.NEEDS_DYNAMIC_PROBE),
        )

        cases.forEachIndexed { index, (label, mapping, expected) ->
            val target = target("m$index", "b$index", mapping)
            val prefix = if (expected == CapabilityStatus.EXTERNAL_ONLY) {
                completePrefix(target, CapabilityAstFact(label, "external:fixture-adapter"))
            } else {
                completePrefix(target, label)
            }
            val record = StaticCapabilityClassifier.classify(target, prefix)
            assertEquals(expected, record.staticStatus)
            assertEquals(label, record.primaryLabel)
            assertEquals(listOf(label), record.labels)
        }

        val unknownTarget = target("unknown", "unknown", "exact")
        val unknownPrefix = completePrefix(
            unknownTarget,
            CapabilityAstFact(CapabilityAstKind.UNKNOWN, "new-front-end-node", proven = false),
        )
        assertEquals(
            CapabilityStatus.NEEDS_DYNAMIC_PROBE,
            StaticCapabilityClassifier.classify(unknownTarget, unknownPrefix).staticStatus,
        )
    }

    @Test
    fun `optional unsupported arm probes while a mandatory unsupported prefix is terminal`() {
        val target = target("diamond", "diamond#b", "exact", stmtIndex = 3)
        val optional = CapabilityPrefixSlicer.slice(
            methodId = target.methodId,
            branchId = target.branchId,
            targetStmtIndex = 3,
            cfg = CapabilityCfg(
                0,
                listOf(
                    node(0, 1, 2),
                    node(1, 3, fact = CapabilityLabel.SPREAD_YIELD),
                    node(2, 3),
                    node(3),
                ),
            ),
        )
        assertEquals(listOf(0, 3), optional.mandatoryStmtIndices)
        assertEquals(
            CapabilityStatus.NEEDS_DYNAMIC_PROBE,
            StaticCapabilityClassifier.classify(target, optional).staticStatus,
        )

        val mandatory = CapabilityPrefixSlicer.slice(
            methodId = target.methodId,
            branchId = target.branchId,
            targetStmtIndex = 3,
            cfg = CapabilityCfg(
                0,
                listOf(
                    node(0, 1, fact = CapabilityLabel.SPREAD_YIELD),
                    node(1, 2, 3),
                    node(2, 3),
                    node(3),
                ),
            ),
        )
        assertTrue(mandatory.mandatoryFacts.any { it.kind == CapabilityLabel.SPREAD_YIELD })
        assertEquals(
            CapabilityStatus.UNSUPPORTED,
            StaticCapabilityClassifier.classify(target, mandatory).staticStatus,
        )
    }

    @Test
    fun `unproved unsupported facts stay probeable and cannot be promoted by another label`() {
        val target = target("unproved", "unproved#b", "exact")
        val prefix = completePrefix(
            target,
            CapabilityAstFact(CapabilityLabel.SPREAD_YIELD, "lexical:spread", proven = false),
            CapabilityAstFact(CapabilityLabel.ITERATOR, "lexical:iterator", proven = false),
        )

        val record = StaticCapabilityClassifier.classify(target, prefix)

        assertEquals(CapabilityStatus.NEEDS_DYNAMIC_PROBE, record.staticStatus)
        assertEquals("unproved_ast_fact_requires_probe", record.reasonCode)
    }

    @Test
    fun `versioned policy changes both classifier version and manifest hash deterministically`() {
        val target = target("policy", "policy#b", "exact")
        val prefix = completePrefix(target, CapabilityLabel.ITERATOR)
        val defaultReport = CapabilityScanner.scan(listOf(target), listOf(prefix))
        val custom = CapabilityPolicy(
            version = "iterator-external-v1",
            rules = RoadmapCapabilityPolicy.default.rules +
                (CapabilityLabel.ITERATOR to CapabilityPolicyRule(CapabilityStatus.EXTERNAL_ONLY)),
        )
        val first = CapabilityScanner.scan(listOf(target), listOf(prefix), custom)
        val second = CapabilityScanner.scan(listOf(target), listOf(prefix), custom)

        assertEquals(first, second)
        assertEquals("$CAPABILITY_CLASSIFIER_BASE_VERSION:iterator-external-v1", first.classifierVersion)
        assertEquals(CapabilityStatus.EXTERNAL_ONLY, first.records.single().staticStatus)
        assertTrue(first.manifestHash != defaultReport.manifestHash)
        assertTrue(first.classifierVersion != defaultReport.classifierVersion)
    }

    @Test
    fun `scan covers every residual exactly once and hash ignores input ordering`() {
        val first = target("a", "a#b", "exact", stmtIndex = 1)
        val second = target("z", "z#b", "oneToMany", stmtIndex = 2)
        val firstPrefix = completePrefix(first, CapabilityLabel.PRIMITIVE_ARITHMETIC)
        val secondPrefix = completePrefix(second, CapabilityLabel.ARRAY_OBJECT)

        val forward = CapabilityScanner.scan(listOf(first, second), listOf(firstPrefix, secondPrefix))
        val reverse = CapabilityScanner.scan(listOf(second, first), listOf(secondPrefix, firstPrefix))

        assertEquals(forward, reverse)
        assertEquals(2, forward.records.size)
        assertEquals(listOf("a", "z"), forward.records.map(StaticCapabilityRecord::methodId))
        assertEquals(1, forward.statusCounts.getValue(CapabilityStatus.SUPPORTED))
        assertEquals(1, forward.statusCounts.getValue(CapabilityStatus.NEEDS_DYNAMIC_PROBE))
        assertTrue(CapabilityReportValidator.validate(forward, setOf(first.key(), second.key())).valid)
        assertEquals(forward, CapabilityReportCodec.decode(CapabilityReportCodec.encode(forward)))
        assertEquals(
            2,
            CapabilityReportCodec.encodeRecordsJsonl(forward).lineSequence().filter(String::isNotBlank).count(),
        )
    }

    @Test
    fun `missing duplicate and unknown capability records are rejected`() {
        val target = target("m", "m#b", "exact")
        val report = CapabilityScanner.scan(
            listOf(target),
            listOf(completePrefix(target, CapabilityLabel.PRIMITIVE_ARITHMETIC)),
        )
        val expected = setOf(target.key(), CapabilityTargetKey("missing", "missing#b"))
        assertTrue(CapabilityReportValidator.validate(report, expected).issues.any { it.code == "missing_target" })

        val duplicate = report.copy(
            sourceTargetCount = 2,
            statusCounts = report.statusCounts + (CapabilityStatus.SUPPORTED to 2),
            labelCounts = report.labelCounts + (CapabilityLabel.PRIMITIVE_ARITHMETIC to 2),
            records = report.records + report.records.single(),
        )
        assertTrue(CapabilityReportValidator.validate(duplicate).issues.any { it.code == "duplicate_target" })

        val unknown = report.copy(
            labelCounts = report.labelCounts - CapabilityLabel.PRIMITIVE_ARITHMETIC + ("future_label" to 1),
            records = listOf(
                report.records.single().copy(labels = listOf("future_label"), primaryLabel = "future_label"),
            ),
        )
        val issues = CapabilityReportValidator.validate(unknown).issues
        assertTrue(issues.any { it.code == "unknown_label" })
    }

    @Test
    fun `source targets must pass v2 contract before scanning`() {
        val valid = target("m", "m#b", "exact")
        val validPath = tempDir.resolve("source-targets.jsonl")
        validPath.writeText(ArtifactContractCodec.encodeSourceTargets(listOf(valid)))
        val report = CapabilityScanner.scan(validPath) {
            completePrefix(it, CapabilityLabel.PRIMITIVE_ARITHMETIC)
        }
        assertEquals(1, report.sourceTargetCount)

        val invalidPath = tempDir.resolve("invalid-source-targets.jsonl")
        invalidPath.writeText(
            ArtifactContractCodec.encodeSourceTargets(listOf(valid)).replace("\"exact\"", "\"guessed\""),
        )
        val failure = assertThrows(CapabilityContractException::class.java) {
            CapabilityScanner.scan(invalidPath) { null }
        }
        assertTrue(failure.issues.any { it.code.startsWith("source_targets_") })
    }

    @Test
    fun `replay provider closes probes and resolves supported-with-flag for the actual run`() {
        val primitive = target("primitive", "primitive#b", "exact")
        val callable = target("callable", "callable#b", "exact")
        val ambiguous = target("ambiguous", "ambiguous#b", "ambiguous")
        val report = CapabilityScanner.scan(
            listOf(primitive, callable, ambiguous),
            listOf(
                completePrefix(primitive, CapabilityLabel.PRIMITIVE_ARITHMETIC),
                completePrefix(callable, CapabilityLabel.CALLABLE),
                completePrefix(ambiguous, CapabilityLabel.PRIMITIVE_ARITHMETIC),
            ),
        )
        val probes = mapOf(
            ambiguous.key() to CapabilityDynamicProbeResult(DynamicProbeOutcome.SUPPORTED, "fixture_replayed"),
        )

        assertThrows(IllegalArgumentException::class.java) { ScannedCapabilityReplayProvider(report) }
        val disabled = ScannedCapabilityReplayProvider(report, dynamicProbes = probes)
        assertEquals(StaticCapabilityLabel.SUPPORTED, disabled.assess(primitive).terminalStatus)
        assertEquals(StaticCapabilityLabel.UNSUPPORTED, disabled.assess(callable).terminalStatus)
        assertEquals(DynamicProbeOutcome.SUPPORTED, disabled.assess(ambiguous).dynamicProbeOutcome)

        val enabled = ScannedCapabilityReplayProvider(report, setOf("callableValueModel"), probes)
        assertEquals(StaticCapabilityLabel.SUPPORTED, enabled.assess(callable).terminalStatus)
    }

    @Test
    fun `EtsIR bridge computes a real arithmetic prefix for a mapped edge`() {
        val method = branchingMethod()
        val manifestMethod = TargetManifest.fromMethods(listOf(method)).methods.single()
        val branch = manifestMethod.branches.first()
        val target = target(
            methodId = manifestMethod.methodId,
            branchId = branch.branchId,
            mapping = "exact",
            stmtIndex = branch.ifStmtIndex,
            successorStmtIndex = branch.successorStmtIndex,
            successorOrdinal = branch.successorOrdinal,
        )

        val prefix = EtsIrCapabilityPrefixBuilder.build(method, target)

        assertTrue(prefix.complete)
        assertTrue(prefix.facts.any { it.kind == CapabilityAstKind.PRIMITIVE_ARITHMETIC })
        assertEquals(CapabilityStatus.SUPPORTED, StaticCapabilityClassifier.classify(target, prefix).staticStatus)
    }

    private fun node(
        index: Int,
        vararg successors: Int,
        fact: String? = null,
    ): CapabilityCfgNode = CapabilityCfgNode(
        stmtIndex = index,
        successorStmtIndices = successors.toList(),
        facts = fact?.let { listOf(CapabilityAstFact(it, "fixture:$it")) }.orEmpty(),
    )

    private fun completePrefix(target: SourceTargetRecord, label: String): CapabilityPrefixSlice =
        completePrefix(target, CapabilityAstFact(label, "fixture:$label"))

    private fun completePrefix(target: SourceTargetRecord, vararg facts: CapabilityAstFact): CapabilityPrefixSlice =
        CapabilityPrefixSlice(
            methodId = target.methodId,
            branchId = target.branchId,
            targetStmtIndex = target.stmtIndex,
            conservativeStmtIndices = (0..target.stmtIndex).toList(),
            mandatoryStmtIndices = (0..target.stmtIndex).toList(),
            mandatoryFacts = facts.toList(),
            facts = facts.toList(),
            complete = true,
        )

    private fun target(
        methodId: String,
        branchId: String,
        mapping: String,
        stmtIndex: Int = 1,
        successorStmtIndex: Int = 2,
        successorOrdinal: Int = 0,
    ): SourceTargetRecord = SourceTargetRecord(
        methodId = methodId,
        branchId = branchId,
        stmtIndex = stmtIndex,
        successorStmtIndex = successorStmtIndex,
        successorOrdinal = successorOrdinal,
        tsSourceRange = ArtifactSourceRange("fixture.ts", 0, 1, 0, 0, 0, 1),
        sourceOrigin = SourceCallableOrigin("fixture.ts", "fixture", "free"),
        mappingStatus = mapping,
    )

    private fun branchingMethod(): EtsMethod {
        val body = program {
            val x = local("x")
            assign(x, param(0))
            ifStmt(lt(x, const(7.0))) {
                ret(const(1.0))
            }
            ret(const(0.0))
        }
        return EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = "%dflt",
                    file = EtsFileSignature(projectName = "fixture", fileName = "fixture.ts"),
                ),
                name = "fixture",
                parameters = listOf(EtsMethodParameter(0, "x", EtsUnknownType)),
                returnType = EtsUnknownType,
            ),
        ).also { method -> method.body.cfg = body.toBlockCfg().toEtsBlockCfg(method) }
    }
}
