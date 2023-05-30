package org.usvm.instrumentation.testcase

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.util.UTestCreator
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

class UTestExpressionExecutorTest {

    companion object {

        const val testJarPath = "build/libs/usvm-instrumentation-test.jar"
        lateinit var jcClasspath: JcClasspath

        @BeforeAll
        @JvmStatic
        fun init() = runBlocking {
            val cp = listOf(File(testJarPath).absoluteFile)
            val db = jacodb {
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
            }
            jcClasspath = db.classpath(cp)
        }

        fun createTestExecutor(): UTestExpressionExecutor {
            val classLoader = WorkerClassLoader(
                arrayOf(Paths.get(testJarPath).toUri().toURL()),
                this::class.java.classLoader,
                "",
                jcClasspath
            )
            return UTestExpressionExecutor(classLoader)
        }

    }

    @Test
    fun testArrayCreation() {
        val statements = UTestCreator.A.indexOf(jcClasspath).let { it.initStatements + listOf(it.callMethodExpression) }
        val executor = createTestExecutor()
        executor.executeUTestExpressions(statements.dropLast(1))
        val res = executor.executeUTestExpression(statements.last()).getOrThrow()
        assertEquals(5, res)
    }

    @Test
    fun testObjectArrayCreation() {
        val statements = UTestCreator.A.indexOfT(jcClasspath).let { it.initStatements + listOf(it.callMethodExpression) }
        val executor = createTestExecutor()
        executor.executeUTestExpressions(statements.dropLast(1))
        val res = executor.executeUTestExpression(statements.last()).getOrThrow()
        assertEquals(5, res)
    }

}