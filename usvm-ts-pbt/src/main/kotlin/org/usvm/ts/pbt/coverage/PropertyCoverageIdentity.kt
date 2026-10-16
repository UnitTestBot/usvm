package org.usvm.ts.pbt.coverage

import org.usvm.ts.pbt.backend.PropertyCoverageIdentity
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Collectors

/** Snapshots the declared support/oracle and the local TypeScript files used by the concrete runtime. */
fun captureCoverageIdentity(manifest: PropertyManifest, sourceRoots: List<Path>): PropertyCoverageIdentity {
    val sources = sortedMapOf<String, String>()
    for (root in sourceRoots) {
        val files = Files.walk(root).use { paths ->
            paths.filter { isCoverageSource(root, it) }.collect(Collectors.toList())
        }
        for (path in files) {
            sources[path.toRealPath().toString()] = sha256(Files.readAllBytes(path))
        }
    }

    val manifestBytes = PropertyManifestJson.encode(manifest).toByteArray(Charsets.UTF_8)
    return PropertyCoverageIdentity(
        manifestSha256 = sha256(manifestBytes),
        sourceSha256 = sources,
    )
}

private fun isCoverageSource(root: Path, path: Path): Boolean {
    val excluded = root.relativize(path).any { it.toString() in EXCLUDED_DIRECTORIES }
    val typeScript = path.toString().endsWith(".ts") || path.toString().endsWith(".ets")
    return !excluded && typeScript && Files.isRegularFile(path)
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }

private val EXCLUDED_DIRECTORIES = setOf("node_modules", ".git", ".gradle", "build", "dist")
