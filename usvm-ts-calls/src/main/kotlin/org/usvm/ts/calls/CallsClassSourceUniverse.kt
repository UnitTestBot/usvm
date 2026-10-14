package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.fastcheck.TypeScriptClassStatementDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Frozen original-source executable statements, independent of policy, seed and replay hits. */
@Serializable
internal data class CallsClassSourceUniverse(
    val sourceFile: String,
    val className: String,
    val sourceSha256: String,
    val universeSha256: String,
    val instrumenterVersion: String,
    val statements: List<TypeScriptClassStatementDescriptor>,
) {
    init {
        require(sourceFile.isNotBlank() && className.isNotBlank())
        require(statements.isNotEmpty()) { "Class coverage universe has no executable statements" }
        require(statements.map { statement -> statement.id }.toSet().size == statements.size) {
            "Class statement IDs must be unique"
        }
        val hasExactOriginalRanges = statements.all { statement ->
            statement.startOffset >= 0 && statement.endOffset > statement.startOffset &&
                statement.id.startsWith("$sourceSha256:")
        }
        require(hasExactOriginalRanges) { "Class statements must identify exact original-source AST ranges" }
    }

    val statementIds: Set<String>
        get() = statements.mapTo(linkedSetOf()) { statement -> statement.id }

    fun verifyUnchanged(sourceRoot: Path) {
        val root = sourceRoot.toRealPath()
        val source = root.resolve(sourceFile).normalize().toRealPath()
        require(source.startsWith(root)) { "Class source is outside the frozen source root" }
        require(callsClassSourceSha256(Files.readAllBytes(source)) == sourceSha256) {
            "Class source changed after freezing its statement universe"
        }
    }

    /** Counter IDs are native hits of these exact AST statements, never inferred from c8 line records. */
    fun checkedCoveredIds(nativeHits: Collection<String>): Set<String> {
        val covered = nativeHits.toSet()
        val unknownIds = covered - statementIds
        require(unknownIds.isEmpty()) { "Native class coverage reported unknown statement IDs: $unknownIds" }

        return covered
    }
}

internal fun callsClassSourceSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK) }

private const val UNSIGNED_BYTE_MASK = 0xff
