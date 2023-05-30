package org.usvm.instrumentation.executor

import com.jetbrains.rd.framework.util.NetUtils
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.jacodb.transform.JcInstrumenter
import org.usvm.instrumentation.jacodb.transform.JcInstrumenterFactory
import org.usvm.instrumentation.rd.InstrumentedProcess
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.statement.UTestExecutionResult
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import osSpecificJavaExecutable
import java.io.File
import kotlin.reflect.KClass

class InstrumentationProcessRunner(
    private val testingProjectClasspath: String,
    private val jcClasspath: JcClasspath,
    private val instrumentationClassFactory: KClass<out JcInstrumenterFactory<out JcInstrumenter>>
) {

    private lateinit var processRunner: ProcessRunner
    private lateinit var lifetime: Lifetime

    constructor(
        testingProjectClasspath: List<String>,
        jcClasspath: JcClasspath,
        instrumentationClassFactory: KClass<JcInstrumenterFactory<out JcInstrumenter>>
    ) : this(testingProjectClasspath.joinToString(":"), jcClasspath, instrumentationClassFactory)

    fun isAlive() = this::lifetime.isInitialized && lifetime.isAlive

    private val workerProcessArgs: List<String> by lazy {
        val instrumentationClassNameFactoryName = instrumentationClassFactory.java.name
        val memoryLimit = listOf("-Xmx1g")
        val pathToJava = JdkInfoService.provide().path
        val usvmClasspath = System.getProperty("java.class.path")
        val javaVersionSpecificArguments = OpenModulesContainer.javaVersionSpecificArguments
        val instrumentedProcessClassName =
            InstrumentedProcess::class.qualifiedName ?: error("Can't find instumented process")
        listOf(pathToJava.resolve("bin${File.separatorChar}${osSpecificJavaExecutable()}").toString()) +
                listOf("-javaagent:${InstrumentationModuleConstants.pathToUsvmInstrumentationJar}=$instrumentationClassNameFactoryName") +
                memoryLimit +
                javaVersionSpecificArguments +
                listOf("-classpath", usvmClasspath) +
                listOf(instrumentedProcessClassName, testingProjectClasspath)
    }

    suspend fun init(parentLifetime: Lifetime) {
        val processLifetime = LifetimeDefinition(parentLifetime)
        lifetime = processLifetime
        val rdPort = NetUtils.findFreePort(0)
        val workerCommand = workerProcessArgs + listOf("$rdPort")
        val pb = ProcessBuilder(workerCommand).inheritIO()
        val process = pb.start()
        processRunner =
            ProcessRunner(process = process, rdPort = rdPort, jcClasspath = jcClasspath, lifetime = processLifetime)
        processRunner.init()
    }

    suspend fun executeUTest(uTest: UTest): UTestExecutionResult = processRunner.callUTest(uTest)

    suspend fun destroy() = processRunner.destroy()


}
