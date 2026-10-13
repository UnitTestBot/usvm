package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** The same frozen source identity is used by search journals, replay and coverage universes. */
@Serializable
internal data class CallsCoverageSourceIdentity(
    val manifestSha256: String,
    val sourceRoot: String,
    val revision: String,
)

internal class CallsCoverageSource private constructor(
    val manifestPath: Path,
    val manifest: CallsExperimentManifest,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val sourceRoot: Path,
    val identity: CallsCoverageSourceIdentity,
) {
    /** Never use the symbolic engine's verify-once cache at a persisted experiment boundary. */
    fun verifyUnchanged() {
        val manifestSha256 = sha256(Files.readAllBytes(manifestPath))
        require(manifestSha256 == identity.manifestSha256) { "Coverage source manifest changed" }
        val currentRoot = manifestPath.parent.resolve(project.sourceRoot).toRealPath()
        require(currentRoot == sourceRoot) { "Coverage source root changed" }

        verifyCallsGitCheckout(sourceRoot, identity.revision)
        val checkoutStatus = runCallsGit(sourceRoot, "status", "--porcelain", "--untracked-files=all")
        require(checkoutStatus.isBlank()) { "Coverage source checkout must be clean: $checkoutStatus" }
    }

    companion object {
        fun load(manifestPath: Path, projectIndex: Int, functionIndex: Int): CallsCoverageSource {
            val realManifestPath = manifestPath.toRealPath()
            val manifestBytes = Files.readAllBytes(realManifestPath)
            val manifest = CallsExperimentJson.decodeManifest(manifestBytes.toString(Charsets.UTF_8))
            val project = manifest.projects[projectIndex]
            val function = project.functions[functionIndex]
            val sourceRoot = realManifestPath.parent.resolve(project.sourceRoot).toRealPath()
            val source = CallsCoverageSource(
                manifestPath = realManifestPath,
                manifest = manifest,
                project = project,
                function = function,
                sourceRoot = sourceRoot,
                identity = CallsCoverageSourceIdentity(
                    manifestSha256 = sha256(manifestBytes),
                    sourceRoot = sourceRoot.toString(),
                    revision = project.revision,
                ),
            )
            source.verifyUnchanged()

            return source
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
