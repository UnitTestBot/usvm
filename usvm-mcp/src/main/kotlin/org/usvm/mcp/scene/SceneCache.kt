package org.usvm.mcp.scene

import mu.KotlinLogging
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.mcp.McpToolException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Loads and caches [EtsScene]s built from single `.ts` files.
 *
 * Conversion `.ts -> ETS IR` uses the selected JacoDB frontend, with the bundled
 * TypeScript frontend as the default. Scenes are cached in memory, keyed by the
 * real path and invalidated by mtime.
 */
class SceneCache {

    private data class Entry(val mtime: FileTime, val scene: EtsScene)

    private val cache = ConcurrentHashMap<Path, Entry>()

    fun getScene(file: String): EtsScene {
        val path = resolveTsFile(file)
        val mtime = Files.getLastModifiedTime(path)
        val cached = cache[path]
        if (cached != null && cached.mtime == mtime) {
            return cached.scene
        }
        val provider = EtsIrProvider.default()
        if (provider == EtsIrProvider.ARKANALYZER) {
            checkArkAnalyzerAvailable()
        }

        val setupHint = when (provider) {
            EtsIrProvider.TS_FRONTEND -> "Check that Node.js is installed."
            EtsIrProvider.ARKANALYZER ->
                "Check that ArkAnalyzer is built (ARKANALYZER_DIR must point to a checkout " +
                    "with 'npm install && npm run build' done)."
        }

        logger.info { "Converting $path to ETS IR with $provider..." }
        val etsFile = try {
            // Type inference gives the machine precise `number`/`boolean` types,
            // significantly reducing spurious paths caused by untyped values.
            loadEtsFileAutoConvert(path, useArkAnalyzerTypeInference = 1, provider = provider)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            throw McpToolException(
                "Failed to convert '$path' to ETS IR with $provider: ${e.message}. " +
                    "Check that the file is valid TypeScript. $setupHint"
            )
        }
        val scene = EtsScene(listOf(etsFile))
        cache[path] = Entry(mtime, scene)
        return scene
    }

    private fun resolveTsFile(file: String): Path {
        val path = try {
            Paths.get(file).toRealPath()
        } catch (@Suppress("SwallowedException") e: java.io.IOException) {
            throw McpToolException(
                "File not found: '$file'. Provide an absolute path (or a path relative " +
                    "to the server working directory) to an existing .ts file."
            )
        }
        if (!Files.isRegularFile(path)) {
            throw McpToolException("'$path' is not a regular file. Provide a path to a .ts file.")
        }
        if (!path.fileName.toString().endsWith(".ts")) {
            throw McpToolException(
                "'$path' does not look like a TypeScript file (expected a '.ts' extension). " +
                    "This server analyzes TypeScript sources only."
            )
        }
        return path
    }

    private fun checkArkAnalyzerAvailable() {
        val dirValue = System.getenv(ARKANALYZER_DIR_ENV)
            ?: throw McpToolException(
                "Environment variable $ARKANALYZER_DIR_ENV is not set. It must point to a checkout of " +
                    "ArkAnalyzer (https://gitee.com/openharmony-sig/arkanalyzer) built with " +
                    "'npm install && npm run build'. Restart the MCP server with this variable set."
            )
        val dir = Paths.get(dirValue)
        if (!Files.isDirectory(dir)) {
            throw McpToolException("$ARKANALYZER_DIR_ENV points to '$dirValue', which is not a directory.")
        }
        val script = dir.resolve(SERIALIZE_SCRIPT)
        if (!Files.isRegularFile(script)) {
            throw McpToolException(
                "ArkAnalyzer build artifact not found: '$script'. " +
                    "Run 'npm install && npm run build' in '$dirValue'."
            )
        }
    }

    companion object {
        private const val ARKANALYZER_DIR_ENV = "ARKANALYZER_DIR"
        private const val SERIALIZE_SCRIPT = "out/src/save/serializeArkIR.js"
    }
}
