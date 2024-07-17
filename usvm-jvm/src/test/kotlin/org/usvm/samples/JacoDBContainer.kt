package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.usvm.util.classpathWithApproximations
import java.io.File

class JacoDBContainer(
    key: Any?,
    classpath: List<File>,
    builder: JcSettings.() -> Unit,
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

            val cp = if (samplesWithApproximationsKey == key) {
                db.classpathWithApproximations(classpath)
            } else {
                db.classpath(classpath)
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
            builder: JcSettings.() -> Unit = defaultBuilder,
        ): JacoDBContainer =
            keyToJacoDBContainer.getOrPut(key) { JacoDBContainer(key, classpath, builder) }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Usages)
        }
    }
}

const val samplesKey = "tests"
const val samplesWithApproximationsKey = "samplesWithApproximations"
