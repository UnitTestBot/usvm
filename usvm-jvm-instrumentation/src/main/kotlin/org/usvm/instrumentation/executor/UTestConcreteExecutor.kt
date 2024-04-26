package org.usvm.instrumentation.executor

import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import kotlinx.coroutines.withTimeout
import org.jacodb.api.jvm.JcClasspath
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.descriptor.UTestUnexpectedExecutionBuilder
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.UTestExecutorInitException
import java.io.File
import kotlin.reflect.KClass
import kotlin.time.Duration

class UTestConcreteExecutor(
    instrumentationClassFactory: KClass<out JcInstrumenterFactory<*>>,
    testingProjectClasspath: String,
    private val jcClasspath: JcClasspath,
    private val timeout: Duration
) : AutoCloseable {

    constructor(
        instrumentationClassFactory: KClass<out JcInstrumenterFactory<*>>,
        testingProjectClasspath: List<String>,
        jcClasspath: JcClasspath,
        timeout: Duration
    ) : this(instrumentationClassFactory, testingProjectClasspath.joinToString(File.pathSeparator), jcClasspath, timeout)

    private val lifetime = LifetimeDefinition()

    private val instrumentationProcessRunner =
        InstrumentationProcessRunner(testingProjectClasspath, jcClasspath, instrumentationClassFactory)
    private val uTestUnexpectedExecutionBuilder = UTestUnexpectedExecutionBuilder(jcClasspath)

    suspend fun ensureRunnerAlive() {
        check(lifetime.isAlive) { "Executor already closed" }
        for (i in 0..InstrumentationModuleConstants.triesToRecreateExecutorRdProcess) {
            if (instrumentationProcessRunner.isAlive()) {
                return
            }
            try {
                instrumentationProcessRunner.init(lifetime)
            } catch (e: Throwable) {
                println("Cant init rdProcess")
            }
        }
        if (!instrumentationProcessRunner.isAlive()) {
            throw UTestExecutorInitException()
        }
    }

    fun executeSync(uTest: UTest): UTestExecutionResult {
        return try {
            instrumentationProcessRunner.executeUTestSync(uTest, timeout)
        } catch (e: Exception) {
            uTestUnexpectedExecutionBuilder.build(e)
        }
    }

    suspend fun executeAsync(uTest: UTest): UTestExecutionResult {
        ensureRunnerAlive()
        return try {
            withTimeout(timeout) {
                instrumentationProcessRunner.executeUTestAsync(uTest)
            }
        } catch (e: Exception) {
            uTestUnexpectedExecutionBuilder.build(e)
        }
    }

    override fun close() {
        lifetime.terminate()
    }

}