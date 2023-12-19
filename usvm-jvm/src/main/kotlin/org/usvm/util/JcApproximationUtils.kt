package org.usvm.util

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcDatabase
import org.jacodb.approximation.Approximations
import org.jacodb.impl.types.JcClassTypeImpl
import org.usvm.machine.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ApproximationPaths(
    val usvmApiJarPath: String? = System.getenv("usvm.jvm.api.jar.path"),
    val usvmApproximationsJarPath: String? = System.getenv("usvm.jvm.approximations.jar.path")
) {
    val namedPaths = mapOf(
        "USVM API" to usvmApiJarPath,
        "USVM Approximations" to usvmApproximationsJarPath
    )
    val presentPaths: Set<String> = namedPaths.values.filterNotNull().toSet()
    val allPathsArePresent = namedPaths.values.all { it != null }
}

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
    features: List<JcClasspathFeature> = emptyList(),
    approximationPaths: ApproximationPaths = ApproximationPaths(),
): JcClasspath {
    if (!approximationPaths.allPathsArePresent) {
        logger.warn {
            "Classpath with approximations is requested, but some jar paths are missing: $approximationPaths"
        }
        return classpath(dirOrJars, features)
    }

    approximationPaths.namedPaths.forEach { (name, path) ->
        logger.info { "Load $name: $path" }
    }

    val approximationsPath = approximationPaths.presentPaths.map { File(it) }

    val cpWithApproximations = dirOrJars + approximationsPath
    val featuresWithApproximations = features + listOf(Approximations)
    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
