package org.usvm.instrumentation.executor

import com.jetbrains.rd.framework.util.NetUtils
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.instrumentation.JcInstrumenter
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.instrumentation.rd.InstrumentedProcess
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.OpenModulesContainer
import org.usvm.instrumentation.util.osSpecificJavaExecutable
import java.io.File
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.time.Duration

//Proccess runner wrapper
class InstrumentationProcessRunner(
    private val testingProjectClasspath: String,
    private val jcClasspath: JcClasspath,
    private val jcPersistenceLocation: String?,
    private val instrumentationClassFactory: KClass<out JcInstrumenterFactory<out JcInstrumenter>>
) {

    private lateinit var rdProcessRunner: RdProcessRunner
    private lateinit var lifetime: Lifetime

    constructor(
        testingProjectClasspath: List<String>,
        jcClasspath: JcClasspath,
        jcPersistenceLocation: String?,
        instrumentationClassFactory: KClass<JcInstrumenterFactory<out JcInstrumenter>>
    ) : this(
        testingProjectClasspath.joinToString(File.pathSeparator),
        jcClasspath,
        jcPersistenceLocation,
        instrumentationClassFactory
    )

    fun isAlive() = this::lifetime.isInitialized && lifetime.isAlive

    private val jvmArgs: List<String> by lazy {
        val instrumentationClassNameFactoryName = instrumentationClassFactory.java.name
        val memoryLimit = listOf("-Xmx1g")
        val pathToJava = Paths.get(InstrumentationModuleConstants.pathToJava)
        val usvmClasspath = System.getProperty("java.class.path")
        val javaVersionSpecificArguments = OpenModulesContainer.javaVersionSpecificArguments
        val instrumentedProcessClassName =
            InstrumentedProcess::class.qualifiedName ?: error("Can't find instumented process")
        listOf(pathToJava.resolve("bin${File.separatorChar}${osSpecificJavaExecutable()}").toString()) +
                listOf("-ea") +
                listOf("-javaagent:${InstrumentationModuleConstants.pathToUsvmInstrumentationJar}=$instrumentationClassNameFactoryName") +
                memoryLimit +
                javaVersionSpecificArguments +
                listOf("-classpath", usvmClasspath) +
                listOf(instrumentedProcessClassName)
    }

    private fun createWorkerProcessArgs(rdPort: Int): List<String> = buildList {
        this += listOf("-cp", testingProjectClasspath)
        this += listOf("-t", "${InstrumentationModuleConstants.concreteExecutorProcessTimeout}")
        this += listOf("-p", "$rdPort")

        if (jcPersistenceLocation != null) {
            this += listOf("-persistence", jcPersistenceLocation)
        }
    }

    suspend fun init(parentLifetime: Lifetime) {
        val processLifetime = LifetimeDefinition(parentLifetime)
        lifetime = processLifetime
        val rdPort = NetUtils.findFreePort(0)
        val workerCommand = jvmArgs + createWorkerProcessArgs(rdPort)
        val pb = ProcessBuilder(workerCommand).inheritIO()
        val process = pb.start()
        rdProcessRunner =
            RdProcessRunner(process = process, rdPort = rdPort, jcClasspath = jcClasspath, lifetime = processLifetime)
        rdProcessRunner.init()
    }

    fun executeUTestSync(uTest: UTest, timeout: Duration): UTestExecutionResult =
        rdProcessRunner.callUTestSync(uTest, timeout)

    suspend fun executeUTestAsync(uTest: UTest): UTestExecutionResult = rdProcessRunner.callUTestAsync(uTest)

    fun destroy() = rdProcessRunner.destroy()


}
