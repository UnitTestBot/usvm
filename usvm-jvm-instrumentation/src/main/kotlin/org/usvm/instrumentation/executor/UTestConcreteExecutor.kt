package org.usvm.instrumentation.executor

import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.RdFault
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExecutionFailedResult
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionTimedOutResult
import org.usvm.instrumentation.testcase.descriptor.UTestExceptionDescriptor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.UTestExecutorInitException
import java.util.concurrent.TimeoutException
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
    ) : this(instrumentationClassFactory, testingProjectClasspath.joinToString(":"), jcClasspath, timeout)

    private val lifetime = LifetimeDefinition()

    private val instrumentationProcessRunner =
        InstrumentationProcessRunner(testingProjectClasspath, jcClasspath, instrumentationClassFactory)

    suspend fun ensureRunnerAlive() {
        check(lifetime.isAlive) { "Executor already closed" }
        for (i in 0..InstrumentationModuleConstants.triesToRecreateExecutorRdProcess) {
            if (instrumentationProcessRunner.isAlive()) {
                return
            }
            try {
                instrumentationProcessRunner.init(lifetime)
            } catch (e: Throwable) {
                //TODO replace to logger
                println("Cant init rdProcess:(")
            }
        }
        if (!instrumentationProcessRunner.isAlive()) {
            throw UTestExecutorInitException()
        }
    }

    fun executeSync(uTest: UTest): UTestExecutionResult {
        return try {
            instrumentationProcessRunner.executeUTestSync(uTest, timeout)
        } catch (e: TimeoutException) {
            instrumentationProcessRunner.destroy()
            val descriptor = UTestExceptionDescriptor(
                type = jcClasspath.findClass<Exception>().toType(),
                message = e.message ?: "timeout",
                stackTrace = listOf(),
                raisedByUserCode = false
            )
            UTestExecutionTimedOutResult(descriptor)
        } catch (e: RdFault) {
            instrumentationProcessRunner.destroy()
            val descriptor = UTestExceptionDescriptor(
                type = jcClasspath.findClass<Exception>().toType(),
                message = e.reasonAsText,
                stackTrace = listOf(),
                raisedByUserCode = false
            )
            UTestExecutionFailedResult(descriptor)
        }
    }

    suspend fun executeAsync(uTest: UTest): UTestExecutionResult {
        ensureRunnerAlive()
        return try {
            withTimeout(timeout) {
                instrumentationProcessRunner.executeUTestAsync(uTest)
            }
        } catch (e: TimeoutCancellationException) {
            val descriptor = UTestExceptionDescriptor(
                type = jcClasspath.findClass<Exception>().toType(),
                message = e.message ?: "timeout",
                stackTrace = listOf(),
                raisedByUserCode = false
            )
            UTestExecutionTimedOutResult(descriptor)
        } catch (e: RdFault) {
            val descriptor = UTestExceptionDescriptor(
                type = jcClasspath.findClass<Exception>().toType(),
                message = e.reasonAsText,
                stackTrace = listOf(),
                raisedByUserCode = false
            )
            UTestExecutionFailedResult(descriptor)
        }
    }

    override fun close() {
        lifetime.terminate()
    }

}