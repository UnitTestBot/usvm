package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.util.otherAllClasspath
import java.io.File

class OtherJacoDBContainer(
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
        private val keyToJacoDBContainer = HashMap<Any?, OtherJacoDBContainer>()

        operator fun invoke(
            key: Any?,
            classpath: List<File> = samplesClasspath,
            builder: JcSettings.() -> Unit = defaultBuilder,
        ): OtherJacoDBContainer =
            keyToJacoDBContainer.getOrPut(key) { OtherJacoDBContainer(classpath, builder) }

        private val samplesClasspath = otherAllClasspath.filter { it.name.contains("samples") }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy)
            loadByteCode(samplesClasspath)
        }
    }
}
