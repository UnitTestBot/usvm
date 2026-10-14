package org.usvm.ts.calls

import org.usvm.ts.pbt.fastcheck.TypeScriptFrozenModuleResolution
import org.usvm.ts.pbt.fastcheck.TypeScriptLocalSourceClosure
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import java.nio.file.Files
import java.nio.file.Path

internal const val CALLS_FROZEN_TSCONFIG = ".usvm-frozen.tsconfig.json"

internal fun checkedCallsSourceClosure(root: Path, entry: Path): TypeScriptLocalSourceClosure {
    val closure = TypeScriptSourceInspector.localSourceClosure(sourceRoot = root, source = entry)
    require(closure.reasonCode == null) { "${closure.reasonCode}: ${closure.diagnostic}" }
    return closure
}

/** Copy executable sources and the metadata that selected them; never link back to ambient source trees. */
internal fun copyCallsSourceClosure(root: Path, destination: Path, closure: TypeScriptLocalSourceClosure) {
    val canonicalRoot = root.toRealPath()
    val inputs = closure.resolution?.inputSha256?.keys.orEmpty() + closure.files
    for (relative in inputs.distinct()) {
        val input = canonicalRoot.resolve(relative).normalize().toRealPath()
        val output = destination.resolve(relative).normalize()
        require(input.startsWith(canonicalRoot) && output.startsWith(destination)) {
            "Frozen module input escapes its root: $relative"
        }
        val expected = closure.resolution?.inputSha256?.get(relative)
        if (expected != null) {
            val actual = callsClassSourceSha256(Files.readAllBytes(input))
            require(actual == expected) { "Frozen module input changed: $relative" }
        }
        Files.createDirectories(output.parent)
        Files.copy(input, output)
    }
}

internal fun writeCallsFrozenTsconfig(root: Path, resolution: TypeScriptFrozenModuleResolution?): Path {
    val config = root.resolve(CALLS_FROZEN_TSCONFIG)
    val content = resolution?.tsconfig ?: """
        {"compilerOptions":{"target":"ES2022","module":"ESNext","moduleResolution":"Node10","types":[]}}
    """.trimIndent()
    Files.writeString(config, content)
    return config
}

internal fun verifyCallsFrozenResolution(root: Path, resolution: TypeScriptFrozenModuleResolution) {
    val canonicalRoot = root.toRealPath()
    for ((relative, expected) in resolution.inputSha256) {
        val source = canonicalRoot.resolve(relative).normalize().toRealPath()
        require(source.startsWith(canonicalRoot)) { "Frozen resolution input escapes root: $relative" }
        val actual = callsClassSourceSha256(Files.readAllBytes(source))
        require(actual == expected) { "Frozen module input changed: $relative" }
    }
}
