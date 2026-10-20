package org.usvm.experiments

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.machine.interpreter.transformers.JcMultiDimArrayAllocationTransformer
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import java.io.File

internal suspend fun withPilotClasspath(
    libraries: List<File>,
    approximationsEnabled: Boolean,
    action: (JcClasspath) -> Unit,
) {
    val database = jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy)
        if (approximationsEnabled) installFeatures(Approximations(emptyList()))
        loadByteCode(libraries)
    }
    database.use { db ->
        val features = listOf(JcMultiDimArrayAllocationTransformer, JcStringConcatTransformer)
        val cp = if (approximationsEnabled) {
            val requiredVariables = listOf("usvm.jvm.api.jar.path", "usvm.jvm.approximations.jar.path")
            val artifactsAvailable = requiredVariables.all { name -> System.getenv(name)?.let(::File)?.isFile == true }
            require(artifactsAvailable) {
                "ON requires both actual approximation/API artifacts; refusing a mislabeled bare classpath"
            }
            db.classpathWithApproximations(libraries, features)
        } else {
            db.classpath(libraries, features)
        }
        cp.use { classpath ->
            db.awaitBackgroundJobs()
            action(classpath)
        }
    }
}
