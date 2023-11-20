package org.usvm.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcDatabase
import org.jacodb.approximation.Approximations
import org.usvm.machine.logger
import java.io.File

private const val USVM_API_JAR_PATH = "usvm.jvm.api.jar.path"
private const val USVM_APPROXIMATIONS_JAR_PATH = "usvm.jvm.approximations.jar.path"

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

    val cpWithApproximations = dirOrJars + listOf(File(usvmApiJarPath), File(usvmApproximationsJarPath))
    val featuresWithApproximations = features + listOf(Approximations)
    return classpath(cpWithApproximations, featuresWithApproximations.distinct())
}
