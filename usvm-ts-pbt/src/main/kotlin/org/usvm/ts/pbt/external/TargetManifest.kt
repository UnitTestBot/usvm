package org.usvm.ts.pbt.external

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsStmt
import java.util.IdentityHashMap

private const val TARGET_MANIFEST_SCHEMA_VERSION = 1

fun stableMethodId(method: EtsMethod): String {
    val signature = method.signature
    val clazz = signature.enclosingClass
    val namespace = namespacePath(clazz.namespace)
    val owner = if (namespace.isEmpty()) clazz.name else "$namespace.${clazz.name}"
    return "${clazz.file.fileName}::$owner::${signature.name}/${signature.parameters.size}"
}

fun stableBranchId(method: EtsMethod, ifStmt: EtsIfStmt, successor: EtsStmt): String {
    val stmts = method.cfg.stmts
    val ifStmtIndex = stmts.indexOfFirst { it === ifStmt }
    val successors = method.cfg.successors(ifStmt).toList()
    val successorOrdinal = successors.indexOfFirst { it === successor }
    val successorStmtIndex = stmts.indexOfFirst { it === successor }
    return "${stableMethodId(method)}#s$ifStmtIndex:$successorOrdinal->$successorStmtIndex"
}

private fun namespacePath(namespace: EtsNamespaceSignature?): String = when (namespace) {
    null -> ""
    else -> listOf(namespacePath(namespace.namespace), namespace.name).filter(String::isNotEmpty).joinToString(".")
}

@Serializable
data class TargetManifest(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = TARGET_MANIFEST_SCHEMA_VERSION,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val generator: String = "usvm-ts-pbt",
    val methods: List<TargetMethod>,
) {
    companion object {
        private val json = Json { prettyPrint = true }

        fun fromMethods(methods: List<EtsMethod>): TargetManifest {
            val collisions = methods.groupBy(::stableMethodId).filterValues { it.size > 1 }
            require(collisions.isEmpty()) {
                "stable method-id collisions: ${collisions.keys.sorted().joinToString()}"
            }
            return TargetManifest(methods = methods.map(TargetMethod::fromMethod))
        }

        fun encode(manifest: TargetManifest): String = json.encodeToString(manifest)
    }
}

@Serializable
data class TargetMethod(
    val methodId: String,
    val signature: String,
    val projectName: String,
    val fileName: String,
    val namespace: String?,
    val className: String,
    val methodName: String,
    val arity: Int,
    val parameterTypes: List<String>,
    val parameters: List<TargetParameter>,
    val entryKind: String,
    val branches: List<TargetBranch>,
) {
    companion object {
        fun fromMethod(method: EtsMethod): TargetMethod {
            val signature = method.signature
            val clazz = signature.enclosingClass
            val stmts = method.cfg.stmts
            val indices = IdentityHashMap<Any, Int>().apply {
                stmts.forEachIndexed { index, stmt -> put(stmt, index) }
            }
            val branches = stmts.mapIndexedNotNull { stmtIndex, stmt ->
                if (stmt !is EtsIfStmt) return@mapIndexedNotNull null
                val successors = method.cfg.successors(stmt).toList()
                if (successors.size != 2) return@mapIndexedNotNull null
                successors.mapIndexed { successorOrdinal, successor ->
                    val successorIndex = indices[successor] ?: -1
                    TargetBranch(
                        branchId = "${stableMethodId(method)}#s$stmtIndex:$successorOrdinal->$successorIndex",
                        ifStmtIndex = stmtIndex,
                        successorOrdinal = successorOrdinal,
                        successorStmtIndex = successorIndex,
                        conditionOrigin = stmt.location.origin?.toManifestSourceOrigin(),
                        successorOrigin = successor.location.origin?.toManifestSourceOrigin(),
                    )
                }
            }.flatten()

            val kind = when {
                clazz.name == "%dflt" -> "free"
                method.isStatic -> "static"
                else -> "instance"
            }
            return TargetMethod(
                methodId = stableMethodId(method),
                signature = signature.toString(),
                projectName = clazz.file.projectName,
                fileName = clazz.file.fileName,
                namespace = namespacePath(clazz.namespace).ifEmpty { null },
                className = clazz.name,
                methodName = signature.name,
                arity = signature.parameters.size,
                parameterTypes = signature.parameters.map { it.type.toString() },
                parameters = signature.parameters.map { parameter ->
                    TargetParameter(
                        index = parameter.index,
                        name = parameter.name,
                        type = parameter.type.toString(),
                        optional = parameter.isOptional,
                        rest = parameter.isRest,
                    )
                },
                entryKind = kind,
                branches = branches,
            )
        }
    }
}

@Serializable
data class TargetParameter(
    val index: Int,
    val name: String,
    val type: String,
    val optional: Boolean,
    val rest: Boolean,
)

@Serializable
data class TargetBranch(
    val branchId: String,
    val ifStmtIndex: Int,
    /** EtsIR successor ordering: 0 is true, 1 is false after conversion. */
    val successorOrdinal: Int,
    val successorStmtIndex: Int,
    val conditionOrigin: ManifestSourceOrigin? = null,
    val successorOrigin: ManifestSourceOrigin? = null,
)

@Serializable
data class ManifestSourceOrigin(
    val fileName: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val nodeKind: String,
)

private fun org.jacodb.ets.model.EtsSourceSpan.toManifestSourceOrigin(): ManifestSourceOrigin =
    ManifestSourceOrigin(
        fileName = fileName,
        startOffset = startOffset,
        endOffset = endOffset,
        startLine = startLine,
        startColumn = startColumn,
        endLine = endLine,
        endColumn = endColumn,
        nodeKind = nodeKind,
    )
