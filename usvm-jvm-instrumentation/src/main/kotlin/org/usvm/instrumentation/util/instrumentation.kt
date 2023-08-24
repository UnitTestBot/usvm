package  org.usvm.instrumentation.util
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object OpenModulesContainer {
    private val modulesContainer: List<String>
    val javaVersionSpecificArguments: List<String>
        get() = modulesContainer
            .takeIf { JdkInfoService.provide().version > 8 } ?: emptyList()

    init {
        modulesContainer = buildList {
            openPackage("java.base", "jdk.internal.misc")
            openPackage("java.base", "java.lang")
            openPackage("java.base", "java.lang.reflect")
            openPackage("java.base", "sun.security.provider")
            openPackage("java.base", "jdk.internal.event")
            openPackage("java.base", "jdk.internal.jimage")
            openPackage("java.base", "jdk.internal.jimage.decompressor")
            openPackage("java.base", "jdk.internal.jmod")
            openPackage("java.base", "jdk.internal.jtrfs")
            openPackage("java.base", "jdk.internal.loader")
            openPackage("java.base", "jdk.internal.logger")
            openPackage("java.base", "jdk.internal.math")
            openPackage("java.base", "jdk.internal.misc")
            openPackage("java.base", "jdk.internal.module")
            openPackage("java.base", "jdk.internal.org.objectweb.asm.commons")
            openPackage("java.base", "jdk.internal.org.objectweb.asm.signature")
            openPackage("java.base", "jdk.internal.org.objectweb.asm.tree")
            openPackage("java.base", "jdk.internal.org.objectweb.asm.tree.analysis")
            openPackage("java.base", "jdk.internal.org.objectweb.asm.util")
            openPackage("java.base", "jdk.internal.org.xml.sax")
            openPackage("java.base", "jdk.internal.org.xml.sax.helpers")
            openPackage("java.base", "jdk.internal.perf")
            openPackage("java.base", "jdk.internal.platform")
            openPackage("java.base", "jdk.internal.ref")
            openPackage("java.base", "jdk.internal.reflect")
            openPackage("java.base", "jdk.internal.util")
            openPackage("java.base", "jdk.internal.util.jar")
            openPackage("java.base", "jdk.internal.util.xml")
            openPackage("java.base", "jdk.internal.util.xml.impl")
            openPackage("java.base", "jdk.internal.vm")
            openPackage("java.base", "jdk.internal.vm.annotation")
            openPackage("java.base", "java.util.concurrent.atomic")
            openPackage("java.base", "java.io")
            openPackage("java.base", "java.util.zip")
            openPackage("java.base", "java.util.concurrent")
            openPackage("java.base", "sun.security.util")
            openPackage("java.base", "java.lang.invoke")
            openPackage("java.base", "java.lang.ref")
            openPackage("java.base", "java.lang.constant")
            openPackage("java.base", "java.util")
            add("--illegal-access=warn")
        }
    }

    private fun MutableList<String>.openPackage(module: String, pakage: String) {
        add("--add-opens")
        add("$module/$pakage=ALL-UNNAMED")
    }
}

data class JdkInfo(
    val path: Path,
    val version: Int
)

/**
 * Singleton to enable abstract access to path to JDK.

 * Used in [org.utbot.instrumentation.process.InstrumentedProcessRunner].
 * The purpose is to use the same JDK in [org.utbot.instrumentation.ConcreteExecutor] and in the test runs.
 * This is necessary because the engine can be run from the various starting points, like IDEA plugin, CLI, etc.
 */
object JdkInfoService : PluginService<JdkInfo> {
    var jdkInfoProvider: JdkInfoProvider = JdkInfoDefaultProvider()

    override fun provide(): JdkInfo = jdkInfoProvider.info
}

interface JdkInfoProvider {
    val info: JdkInfo
}

/**
 * Gets [JdkInfo] from the current process.
 */
open class JdkInfoDefaultProvider : JdkInfoProvider {
    override val info: JdkInfo =
        JdkInfo(Paths.get(System.getProperty("java.home")), fetchJavaVersion(System.getProperty("java.version")))
}

fun fetchJavaVersion(javaVersion: String): Int {
    val matcher = "(1\\.)?(\\d+)".toRegex()
    return Integer.parseInt(matcher.find(javaVersion)?.groupValues?.getOrNull(2)!!)
}

interface PluginService<T> {
    fun provide(): T
}

private val javaSpecificationVersion = System.getProperty("java.specification.version")
val isJvm8 = javaSpecificationVersion.equals("1.8")
val isJvm9Plus = !javaSpecificationVersion.contains(".") && javaSpecificationVersion.toInt() >= 9

fun osSpecificJavaExecutable() = if (isWindows) "javaw" else "java"
private val os = System.getProperty("os.name").lowercase(Locale.getDefault())
val isWindows = os.startsWith("windows")
val isUnix = !isWindows
val isMac = os.startsWith("mac")

class UTestExecutorInitException: Exception()