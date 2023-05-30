package org.usvm.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.int
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.statement.*
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File


private val instrumentedPath = "./usvm-instrumentation/tmp/"


suspend fun main() {
    val testingJars =
        "/home/zver/IdeaProjects/usvm/usvm-instrumentation/build/libs/usvm-instrumentation-test.jar"
    val testingClassPath = testingJars.split(":").map { File(it) }
    val db = jacodb {
        //useProcessJavaRuntime()
        loadByteCode(testingClassPath)
        installFeatures(InMemoryHierarchy)
        //persistent(location = "/home/.usvm/jcdb.db", clearOnStart = false)
    }
    val jcClasspath = db.classpath(testingClassPath) ?: error("Can't find jcClasspath")
    val runner = UTestConcreteExecutor(JcRuntimeTraceInstrumenterFactory::class, testingJars, jcClasspath, InstrumentationModuleConstants.timeout)
    val uTest = createTestUTest(jcClasspath)
    val res = runner.execute(uTest)
    println("RES = $res")
    runner.close()
}

private fun createTestUTest(jcClasspath: JcClasspath): UTest {
    val jcClass = jcClasspath.findClass("example.A")
    val jcMethod = jcClass.findMethodOrNull("indexOf")!!
    val constructor = jcClass.constructors.first()
    val instance = UTestConstructorCall(constructor, listOf())
    val arg1 = UTestCreateArrayExpression(jcClasspath.int, UTestIntExpression(10, jcClasspath.int))
    val setStatement = UTestArraySetStatement(
        arrayInstance = arg1,
        index = UTestIntExpression(5, jcClasspath.int),
        setValueExpression = UTestIntExpression(7, jcClasspath.int)
    )
    val arg2 = UTestIntExpression(7, jcClasspath.int)


    val statements = listOf(
        instance,
        arg1,
        setStatement,
        UTestMethodCall(instance, jcMethod, listOf(arg1, arg2))
    )
    return UTest(statements.dropLast(1), statements.last() as UTestMethodCall)
}