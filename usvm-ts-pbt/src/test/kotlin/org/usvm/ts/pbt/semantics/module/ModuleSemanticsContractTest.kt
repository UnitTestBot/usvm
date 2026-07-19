package org.usvm.ts.pbt.semantics.module

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.util.getResourcePath
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText

class ModuleSemanticsContractTest {
    private val resourceRoot: Path = getResourcePath("/semantics/module")
    private val contractPath: Path = resourceRoot.resolve("module-semantics-v1.json")
    private val contract: ModuleSemanticsContract = ModuleSemanticsContractCodec.decode(contractPath)

    @Test
    fun `module semantic contract is dependency-neutral and provenance complete`() {
        val validation = ModuleSemanticsContractValidator.validate(contract, resourceRoot)
        assertTrue(validation.valid, validation.issues.joinToString())
        assertTrue(validation.issues.isEmpty())

        assertEquals(9, contract.cases.size)
        assertEquals(11, contract.witnesses.size)
        assertEquals(
            mapOf("indexOf" to 2, "lastIndexOf" to 2, "remove" to 1, "frequency" to 2, "equals" to 4),
            contract.witnesses
                .groupingBy { it.methodId.substringAfterLast("::").substringBefore('/') }
                .eachCount(),
        )
        assertTrue(contract.witnesses.all { it.etsIrMappingStatus == "unmapped" })
        assertTrue(contract.witnesses.all { it.sourceBindingStatus == "exact" })
        assertTrue(contract.witnesses.all { it.unionKey == "branchId" })
        assertEquals(11, contract.witnesses.map(ModuleSemanticWitness::branchId).distinct().size)

        val negativeOutcomes = contract.cases.associate { it.id to it.expected.outcome }
        assertEquals(ModuleNodeOutcome.LINK_ERROR, negativeOutcomes.getValue("missing-named-import-link-error"))
        assertEquals(ModuleNodeOutcome.LINK_ERROR, negativeOutcomes.getValue("ambiguous-star-reexport-link-error"))
        assertEquals(ModuleNodeOutcome.INITIALIZATION_ERROR, negativeOutcomes.getValue("cycle-temporal-dead-zone"))
    }

    @Test
    fun `Node ESM execution matches every result and trace`() {
        val runner = resourceRoot.resolve("module-spec-runner.cjs")
        val process = ProcessBuilder("node", runner.toString(), contractPath.toString()).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, stderr)

        val actual = ModuleSemanticsContractCodec.json.decodeFromString<ModuleNodeRun>(stdout)
        val expected = ModuleNodeRun(
            protocolVersion = contract.nodeProtocolVersion,
            contractId = contract.contractId,
            cases = contract.cases.map { fixture ->
                ModuleNodeCaseResult(
                    id = fixture.id,
                    outcome = fixture.expected.outcome,
                    result = fixture.expected.result,
                    trace = fixture.expected.trace,
                    errorName = fixture.expected.errorName,
                )
            },
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `implicit absent binding and open taxonomy labels hard fail validation`() {
        val absentIndex = contract.cases.indexOfFirst { it.id == "explicit-absent-namespace-member" }
        val absentCase = contract.cases[absentIndex]
        val implicitAbsent = contract.copy(
            cases = contract.cases.toMutableList().also { cases ->
                cases[absentIndex] = absentCase.copy(explicitAbsentExports = emptyList())
            },
        )
        val absentValidation = ModuleSemanticsContractValidator.validate(implicitAbsent, resourceRoot)
        assertFalse(absentValidation.valid)
        assertTrue(absentValidation.issues.any { it.code == "implicit_undefined_fallback" })

        val labelCase = contract.cases.first()
        val openLabel = contract.copy(
            cases = listOf(
                labelCase.copy(
                    capability = labelCase.capability.copy(labels = labelCase.capability.labels + "namespace_import"),
                ),
            ) + contract.cases.drop(1),
        )
        val labelValidation = ModuleSemanticsContractValidator.validate(openLabel, resourceRoot)
        assertFalse(labelValidation.valid)
        assertTrue(labelValidation.issues.any { it.code == "unknown_label" })
    }

    @Test
    fun `all 11 witnesses join the immutable broad observations and v1 origins`() {
        val evidence = contract.evidence.single()
        val targetManifestPath = repositoryPath(evidence.targetManifestPath)
        val sourceTargetsPath = repositoryPath(evidence.sourceTargetsPath)
        val observationPath = repositoryPath(evidence.observationPath)
        val denominatorPath = repositoryPath(evidence.denominatorPath)
        val baselineRoot = observationPath.parent.parent
        val baselineManifest = parseObject(baselineRoot.resolve("baseline-manifest.json"))

        assertEquals(evidence.targetManifestSha256, sha256(targetManifestPath))
        assertEquals(evidence.sourceTargetsSha256, sha256(sourceTargetsPath))
        assertEquals(evidence.observationSha256, sha256(observationPath))
        assertEquals(evidence.denominatorSha256, sha256(denominatorPath))
        assertEquals(evidence.baselineId, baselineManifest.string("baselineId"))
        val frozenProject = baselineManifest.array("projects")
            .map { it.obj() }
            .single { it.string("id") == evidence.projectId }
        assertEquals(evidence.repository, frozenProject.string("repository"))
        assertEquals(evidence.projectCommit, frozenProject.string("commit"))

        val manifest = parseObject(targetManifestPath)
        val sourceTargets = parseObject(sourceTargetsPath)
        val observations = parseObject(observationPath)
        assertEquals(1, manifest.integer("schemaVersion"))
        assertEquals(1, sourceTargets.integer("schemaVersion"))

        val methods = manifest.array("methods").associateBy { it.obj().string("methodId") }
        val sourceEntries = sourceTargets.array("entries").associateBy { it.obj().string("methodId") }
        val report = observations.array("reports")
            .map { it.obj() }
            .single { candidate ->
                candidate.string("projectId") == evidence.projectId &&
                    candidate.string("scenario") == evidence.scenario &&
                    candidate.string("denominatorScope") == "broad"
            }
        assertEquals(evidence.sourceReport, report.string("sourceReport"))
        assertEquals(evidence.sourceReportSha256, report.string("sourceReportSha256"))
        val observedMethods = report.array("methods").associateBy { it.obj().string("methodId") }
        val denominator = denominatorPath.readLines().toSet()

        contract.witnesses.forEach { witness ->
            assertTrue("${witness.projectId}\t${witness.branchId}" in denominator)
            val method = requireNotNull(methods[witness.methodId]).obj()
            val branch = method.array("branches")
                .map { it.obj() }
                .single { it.string("branchId") == witness.branchId }
            assertEquals(witness.conditionOrigin, branch.origin("conditionOrigin"))
            assertEquals(witness.successorOrigin, branch.origin("successorOrigin"))
            assertEquals(witness.etsIrOriginId, branch.string("branchId"))

            val suffix = requireNotNull(BRANCH_SUFFIX.find(witness.branchId)).destructured
            assertEquals(suffix.component1().toInt(), branch.integer("ifStmtIndex"))
            assertEquals(suffix.component2().toInt(), branch.integer("successorOrdinal"))
            assertEquals(suffix.component3().toInt(), branch.integer("successorStmtIndex"))

            val sourceEntry = requireNotNull(sourceEntries[witness.methodId]).obj()
            assertTrue(sourceEntry.boolean("sourceCallable"))
            assertEquals("arrays.ts", sourceEntry.string("sourceFile"))
            assertEquals(method.string("methodName"), sourceEntry.string("exportName"))
            val canonicalSourceId =
                "ts:${sourceEntry.string("sourceFile")}::free:" +
                    "${sourceEntry.string("exportName")}/${method.integer("arity")}"
            assertEquals(canonicalSourceId, witness.sourceCallableId)

            val observedMethod = requireNotNull(observedMethods[witness.methodId]).obj()
            val target = observedMethod.obj("symbolic").array("targets")
                .map { it.obj() }
                .single { it.string("branchId") == witness.branchId }
            assertEquals(witness.historical.reached, target.boolean("reached"))
            assertEquals(witness.historical.replayConfirmed, target.boolean("replayConfirmed"))
            assertEquals(witness.historical.wallMs, target.long("wallMs"))
            assertEquals(witness.historical.steps, target.integer("steps"))
            assertEquals(witness.historical.hintsUsed, target.boolean("hintsUsed"))
            assertEquals(witness.historical.fallbackUsed, target.boolean("fallbackUsed"))

            val pbt = observedMethod.obj("pbt")
            assertEquals(witness.historical.pbtExecutions, pbt.integer("executions"))
            assertEquals(witness.historical.pbtThrew, pbt.integer("threw"))
            assertTrue(
                pbt.array("failures").single().obj().string("description")
                    .contains("cannot read property 'defaultEquals' of undefined"),
            )
        }
    }

    private fun repositoryPath(relative: String): Path {
        val cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        return listOf(cwd.resolve(relative), cwd.parent.resolve(relative))
            .firstOrNull(Path::exists)
            ?: error("repository artifact '$relative' is not reachable from $cwd")
    }

    private fun parseObject(path: Path): JsonObject =
        Json.parseToJsonElement(path.readText()).jsonObject

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(path.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun JsonElement.obj(): JsonObject = jsonObject
    private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray
    private fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject
    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.integer(name: String): Int = getValue(name).jsonPrimitive.int
    private fun JsonObject.long(name: String): Long = getValue(name).jsonPrimitive.long
    private fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean
    private fun JsonObject.origin(name: String): ModuleSourceOrigin? = get(name)
        ?.takeUnless { it is JsonNull }
        ?.let { ModuleSemanticsContractCodec.json.decodeFromJsonElement(it) }

    @Serializable
    private data class ModuleNodeRun(
        val protocolVersion: Int,
        val contractId: String,
        val cases: List<ModuleNodeCaseResult>,
    )

    @Serializable
    private data class ModuleNodeCaseResult(
        val id: String,
        val outcome: String,
        val result: JsonElement,
        val trace: List<String>,
        val errorName: String? = null,
    )

    private companion object {
        val BRANCH_SUFFIX: Regex = Regex("#s(\\d+):(\\d+)->(\\d+)$")
    }
}
