package org.usvm.util

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.approximation.Approximations
import org.jacodb.impl.types.JcClassTypeImpl
import org.usvm.machine.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val USVM_API_JAR_PATH = "usvm.jvm.api.jar.path"
private const val USVM_APPROXIMATIONS_JAR_PATH = "usvm.jvm.approximations.jar.path"

private val classpathApproximations: MutableMap<JcClasspath, Set<String>> = ConcurrentHashMap()

// TODO: use another way to detect internal classes (e.g. special bytecode location type)
val JcClassOrInterface.isUsvmInternalClass: Boolean
    get() = classpathApproximations[classpath]?.contains(name) ?: false

val JcClassType.isUsvmInternalClass: Boolean
    get() = if (this is JcClassTypeImpl) {
        classpathApproximations[classpath]?.contains(name) ?: false
    } else {
        jcClass.isUsvmInternalClass
    }

suspend fun JcDatabase.classpathWithApproximations(
    dirOrJars: List<File>,
    features: List<JcClasspathFeature> = emptyList()
): JcClasspath {
    val usvmApiJarPath = System.getenv(USVM_API_JAR_PATH)
    val usvmApproximationsJarPath = System.getenv(USVM_APPROXIMATIONS_JAR_PATH)

    if (usvmApiJarPath == null || usvmApproximationsJarPath == null) {
        return classpath(dirOrJars, features)
    }

    logger.info { "Load USVM API: $usvmApiJarPath" }
    logger.info { "Load USVM Approximations: $usvmApproximationsJarPath" }

    val approximationsPath = setOf(File(usvmApiJarPath), File(usvmApproximationsJarPath))

    val cpWithApproximations = dirOrJars + approximationsPath
    val featuresWithApproximations = features + listOf(Approximations)
    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
