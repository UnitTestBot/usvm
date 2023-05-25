import example.A
import example.B
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.*
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.UTestExecutor
import org.usvm.instrumentation.testcase.statement.*
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

class UTestExecutorTest {

    companion object {

        const val testJarPath = "build/libs/usvm-instrumentation-test.jar"
        lateinit var jcClassPath: JcClasspath

        @BeforeAll
        @JvmStatic
        fun init() = runBlocking {
            val cp = listOf(File(testJarPath).absoluteFile)
            val db = jacodb {
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
            }
            jcClassPath = db.classpath(cp)
        }

        fun createTestExecutor(): UTestExecutor {
            val classLoader = WorkerClassLoader(
                arrayOf(Paths.get(testJarPath).toUri().toURL()),
                this::class.java.classLoader,
                "",
                jcClassPath
            )
            return UTestExecutor(classLoader)
        }

    }

    @Test
    fun testArrayCreation() {
        val jcClass = jcClassPath.findClass<A>()
        val jcMethod = jcClass.findMethodOrNull("indexOf")!!
        val constructor = jcClass.constructors.first()
        val instance = UTestConstructorCall(constructor, listOf())
        val arg1 = UTestCreateArrayExpression(jcClassPath.int, UTestIntExpression(10, jcClassPath.int))
        val setStatement = UTestArraySetStatement(
            arrayInstance = arg1,
            index = UTestIntExpression(5, jcClassPath.int),
            setValueExpression = UTestIntExpression(7, jcClassPath.int)
        )
        val arg2 = UTestIntExpression(7, jcClassPath.int)


        val statements = listOf(
            instance,
            arg1,
            setStatement,
            UTestMethodCall(instance, jcMethod, listOf(arg1, arg2))
        )
        val executor = createTestExecutor()
        executor.executeUTestExpressions(statements.dropLast(1))
        val res = executor.executeUTestExpression(statements.last()).getOrThrow()
        assertEquals(5, res)
    }

    @Test
    fun testObjectArrayCreation() {
        val jcClassA = jcClassPath.findClass<A>()
        val jcClassB = jcClassPath.findClass<B>()
        val jcMethod = jcClassA.findMethodOrNull("indexOfT")!!
        val constructorA = jcClassA.constructors.first()
        val constructorB = jcClassB.constructors.first()
        val instanceOfA = UTestConstructorCall(constructorA, listOf())
        val instanceOfB = UTestConstructorCall(constructorB, listOf())
        val setFieldOfB = UTestSetFieldStatement(
            instance = instanceOfB,
            field = jcClassB.findFieldOrNull("f")!!,
            value = UTestIntExpression(239, jcClassPath.int)
        )

        val instanceOfB2 = UTestConstructorCall(constructorB, listOf())
        val setFieldOfB2 = UTestSetFieldStatement(
            instance = instanceOfB2,
            field = jcClassB.findFieldOrNull("f")!!,
            value = UTestIntExpression(239, jcClassPath.int)
        )

        val arg1 = UTestCreateArrayExpression(jcClassB.toType(), UTestIntExpression(10, jcClassPath.int))
        val setStatement = UTestArraySetStatement(
            arrayInstance = arg1,
            index = UTestIntExpression(5, jcClassPath.int),
            setValueExpression = instanceOfB
        )

        val statements = listOf(
            instanceOfA,
            instanceOfB,
            setFieldOfB,
            instanceOfB2,
            setFieldOfB2,
            arg1,
            setStatement,
            UTestMethodCall(instanceOfA, jcMethod, listOf(arg1, instanceOfB2))
        )
        val executor = createTestExecutor()
        executor.executeUTestExpressions(statements.dropLast(1))
        val res = executor.executeUTestExpression(statements.last()).getOrThrow()
        assertEquals(5, res)
    }

}