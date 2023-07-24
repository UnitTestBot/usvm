package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.util.allClasspath
import java.io.File

class JacoDBContainer(
    classpath: List<File>,
    builder: JcSettings.() -> Unit,
) {
    val db: JcDatabase
    val cp: JcClasspath

    init {
        val (db, cp) = runBlocking {
            val db = jacodb(builder)
            db to db.classpath(classpath)
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
            keyToJacoDBContainer.getOrPut(key) { JacoDBContainer(classpath, builder) }

        private val samplesClasspath = allClasspath.filter { it.name.contains("samples") }.ifEmpty {
            listOf(File("../Game_env/usvm-jvm/build/classes/java/samples"), File("../Game_env/usvm-jvm/build/classes/kotlin/samples"))
        }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy)
            loadByteCode(samplesClasspath)
        }
    }
}

const val samplesKey = "tests"