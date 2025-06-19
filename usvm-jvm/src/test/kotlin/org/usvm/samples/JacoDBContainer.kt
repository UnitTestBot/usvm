package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcSettings
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.machine.interpreter.transformers.JcMultiDimArrayAllocationTransformer
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import java.io.File

class JacoDBContainer(
    key: Any?,
    classpath: List<File>,
    builder: JcSettings.() -> Unit,
    additionalFeatures: List<JcClasspathFeature> = emptyList(),
) {
    val db: JcDatabase
    val cp: JcClasspath

    init {
        val (db, cp) = runBlocking {
            val db = jacodb {
                builder()

                if (samplesWithApproximationsKey == key) {
                    installFeatures(Approximations)
                }

                loadByteCode(classpath)
            }

            val features = listOf(
                JcMultiDimArrayAllocationTransformer,
                JcStringConcatTransformer,
            ) + additionalFeatures

            val cp = if (samplesWithApproximationsKey == key) {
                db.classpathWithApproximations(classpath, features)
            } else {
                db.classpath(classpath, features)
            }
            db to cp
        }
        this.db = db
        this.cp = cp
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    companion object {
        private val keyToJacoDBContainer = HashMap<Any?, JacoDBContainer>()

        operator fun invoke(
            key: Any?,
            classpath: List<File>,
            features: List<JcClasspathFeature> = emptyList(),
            builder: JcSettings.() -> Unit = defaultBuilder,
        ): JacoDBContainer =
            keyToJacoDBContainer.getOrPut(key) { JacoDBContainer(key, classpath, builder, features) }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy)
        }
    }
}

const val samplesKey = "tests"
const val samplesWithApproximationsKey = "samplesWithApproximations"
