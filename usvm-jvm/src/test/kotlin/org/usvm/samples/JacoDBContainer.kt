package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.util.allClasspath
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
            val db = jacodb(builder)
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
            classpath: List<File> = samplesClasspath,
            builder: JcSettings.() -> Unit = defaultBuilder,
        ): JacoDBContainer =
            keyToJacoDBContainer.getOrPut(key) { JacoDBContainer(key, classpath, builder) }

        private val samplesClasspath = allClasspath.filter {
            it.name.contains("samples") || it.name.contains("tests")
        }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Approximations)
            loadByteCode(samplesClasspath)
        }
    }
}

const val samplesKey = "tests"
const val samplesWithApproximationsKey = "samplesWithApproximations"
