package org.usvm.instrumentation

import com.jetbrains.rd.framework.util.NetUtils
import org.jacodb.api.ext.*
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.executor.ProcessRunner
import org.usvm.instrumentation.jacodb.util.write
import org.usvm.instrumentation.rd.InstrumentedProcess
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.testcase.statement.*
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.Path


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
    val jcClasspath = db.classpath(testingClassPath)
    val jcClass = jcClasspath.findClass("example.A")
    val jcMethod = jcClass.findMethodOrNull("isA")
    println(jcClasspath.toString() + jcClass.toString())
    val instrumentedJcClassPath = Paths.get(instrumentedPath + jcClass.packageName.replace('.', '/'))
    jcClass.write(instrumentedJcClassPath)



    val port = NetUtils.findFreePort(0)
    val classPath = System.getProperty("java.class.path") ?: error("No class path")
    val entrypointClassName =
        InstrumentedProcess::class.qualifiedName ?: error("Entrypoint class name is not available")
    val javaHome = System.getProperty("java.home")
    val javaExecutable = Path(javaHome).resolve("bin").resolve("java")
    val workerCommand = listOf(
        javaExecutable.toAbsolutePath().toString(),
    ) + listOf(
        "-javaagent:/home/zver/IdeaProjects/usvm/usvm-instrumentation/build/libs/usvm-instrumentation-1.0.jar",
        "-classpath", classPath,
    ) + listOf(
        entrypointClassName
    ) + listOf(
        testingJars, "$port"
    )
    val pb = ProcessBuilder(workerCommand).inheritIO()
    val process = pb.start()

    val processRunner = ProcessRunner(process = process, rdPort = port, jcClasspath = jcClasspath)
    processRunner.init()

    val constructor = jcClass.constructors.first()
    val constructorCall = UTestConstructorCall(constructor, listOf())
    val arg = listOf(UTestIntExpression(1, jcClasspath.int))
    val statements = listOf(
        UTestMethodCall(constructorCall, jcMethod!!, arg)
    )
    val uTest = UTest(emptyList(), statements.single())
    val result = processRunner.callUTest(uTest) as UTestExecutionSuccessResult
    println("RESULT = $result")
    println("LOL239")
    println((result.result as UTestConstantDescriptor.Int).value)
    processRunner.destroy()
}

