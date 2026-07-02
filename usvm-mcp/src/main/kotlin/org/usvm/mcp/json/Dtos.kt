package org.usvm.mcp.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** JSON configuration shared by all tool responses. */
val McpJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class ExceptionDto(
    val type: String,
    val message: String? = null,
    val value: JsonElement? = null,
)

/**
 * One symbolic execution path rendered as a concrete test case:
 * concrete inputs plus the observed outcome.
 */
@Serializable
data class TestCaseDto(
    val kind: String, // SUCCESS | EXCEPTION | UNRESOLVED
    val thisInstance: JsonElement? = null,
    val parameters: List<JsonElement> = emptyList(),
    val returnValue: JsonElement? = null,
    val exception: ExceptionDto? = null,
    val resolutionError: String? = null,
)

@Serializable
data class GenerateTestsResultDto(
    val method: String,
    val testCases: List<TestCaseDto>,
    val note: String,
)

@Serializable
data class CheckExceptionsResultDto(
    val method: String,
    val exceptionalCases: List<TestCaseDto>,
    val totalPathsExplored: Int,
    val note: String,
)

@Serializable
data class ReachabilityResultDto(
    val method: String,
    val stmtIndex: Int,
    val statement: String,
    val status: String, // REACHABLE | NOT_REACHED_WITHIN_BUDGET
    val witness: TestCaseDto? = null,
    val note: String,
)

@Serializable
data class StmtRefDto(
    val index: Int,
    val text: String,
)

@Serializable
data class UnreachableFindingDto(
    val method: String,
    val ifStmtIndex: Int,
    val ifStmt: String,
    val uncoveredSuccessors: List<StmtRefDto>,
)

@Serializable
data class UnreachableCodeResultDto(
    val findings: List<UnreachableFindingDto>,
    val analyzedMethods: List<String>,
    val note: String,
)

@Serializable
data class ParamDto(
    val name: String,
    val type: String,
)

@Serializable
data class MethodDto(
    val name: String,
    val parameters: List<ParamDto>,
    val returnType: String,
    val stmtCount: Int,
)

@Serializable
data class ClassDto(
    val name: String,
    val methods: List<MethodDto>,
)

@Serializable
data class ListMethodsResultDto(
    val file: String,
    val classes: List<ClassDto>,
)

@Serializable
data class StmtDto(
    val index: Int,
    val kind: String,
    val text: String,
    val successors: List<Int>,
)

@Serializable
data class MethodIrResultDto(
    val method: String,
    val statements: List<StmtDto>,
)

@Serializable
data class CounterexampleResultDto(
    val method: String,
    val verdict: String, // COUNTEREXAMPLE_FOUND | NO_COUNTEREXAMPLE_WITHIN_BUDGET
    val counterexamples: List<TestCaseDto>,
    val crashes: List<TestCaseDto>,
    val totalPathsExplored: Int,
    val note: String,
)
