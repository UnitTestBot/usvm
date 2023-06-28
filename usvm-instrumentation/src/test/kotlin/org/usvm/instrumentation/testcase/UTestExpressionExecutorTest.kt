package org.usvm.instrumentation.testcase

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.org.usvm.instrumentation.classloader.MockHelper
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.instrumentation.util.UTestCreator
import java.io.File
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
                URLClassPathLoader(listOf(File(testJarPath))),
                this::class.java.classLoader,
                "",
                "",
                jcClasspath
            )
            val mockHelper = MockHelper(jcClasspath, classLoader)
            return UTestExpressionExecutor(classLoader, mutableSetOf(), mockHelper)
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