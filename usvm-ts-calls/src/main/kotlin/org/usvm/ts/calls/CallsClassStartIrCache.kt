package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class CallsClassStartIrCache(val fingerprint: String, val irSha256: Map<String, String>)

private fun bundledFrontendBinarySha256(): String {
    val location = checkNotNull(EtsIrProvider::class.java.protectionDomain.codeSource).location
    val path = Path.of(location.toURI()).toRealPath()
    if (Files.isRegularFile(path)) return callsArtifactSha256(Files.readAllBytes(path))

    val contents = startupIrFiles(path)
    require(contents.isNotEmpty()) { "Bundled frontend runtime files are absent" }
    return callsArtifactSha256(encodeCallsArtifact(contents).toByteArray(Charsets.UTF_8))
}

internal fun startupIrFingerprint(case: CallsClassStartCase): String {
    val values = mapOf(
        "inputs" to CallsExperimentJson.json.encodeToUtf8SafeString(case.inputSha256.toSortedMap().toMap()),
        "frontendConfig" to case.frontendConfig.orEmpty(),
        "frontendRevision" to CallsBuildIdentity.nativeFrontendRevision,
        "frontendBinarySha256" to bundledFrontendBinarySha256(),
        "projectRevision" to case.projectRevision,
    )
    return callsArtifactSha256(CallsExperimentJson.json.encodeToUtf8SafeString(values).toByteArray())
}

internal fun startupIrFiles(root: Path): Map<String, String> = Files.walk(root).use { paths ->
    paths.filter { Files.isRegularFile(it) }.sorted().toList().associate { file ->
        root.relativize(file).toString() to callsArtifactSha256(Files.readAllBytes(file))
    }
}

/** Cache only immutable frontend output, never a machine or symbolic state. */
internal fun preparedClassStartupIr(case: CallsClassStartCase, scope: Path, output: Path): Path {
    val configured = System.getenv("USVM_TS_STARTUP_IR_CACHE")
    val root = configured?.let(Path::of) ?: output.resolve("ir-cache")
    Files.createDirectories(root)
    val fingerprint = startupIrFingerprint(case)
    val cache = root.resolve(fingerprint)
    val lockPath = root.resolve("$fingerprint.lock")
    FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use {
            return prepareLockedStartupIr(cache = cache, scope = scope, output = output, fingerprint = fingerprint)
        }
    }
}

private fun prepareLockedStartupIr(cache: Path, scope: Path, output: Path, fingerprint: String): Path {
    val identityPath = cache.resolve("identity.json")
    val ir = cache.resolve("ir")
    if (Files.exists(identityPath)) {
        val identity = CallsExperimentJson.json.decodeFromString<CallsClassStartIrCache>(Files.readString(identityPath))
        require(identity.fingerprint == fingerprint && startupIrFiles(ir) == identity.irSha256) {
            "Frozen frontend cache changed"
        }
    } else {
        if (Files.exists(cache)) cache.toFile().deleteRecursively()
        Files.createDirectories(cache)
        val generated = generateEtsIR(
            keepPartialOutputOnFailure = false,
            projectPath = scope,
            isProject = true,
            loadEntrypoints = false,
            timeout = 30.seconds,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        try {
            check(generated.toFile().copyRecursively(ir.toFile(), overwrite = false))
        } finally {
            generated.toFile().deleteRecursively()
        }
        writeCallsArtifactAtomically(
            identityPath,
            encodeCallsArtifact(CallsClassStartIrCache(fingerprint = fingerprint, irSha256 = startupIrFiles(ir))),
        )
    }
    Files.copy(identityPath, output.resolve("frontend-identity.json"))
    return ir
}
