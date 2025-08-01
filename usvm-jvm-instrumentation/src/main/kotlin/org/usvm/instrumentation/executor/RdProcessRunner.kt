package org.usvm.instrumentation.executor

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlinx.coroutines.delay
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.instrumentation.generated.models.*
import org.usvm.instrumentation.rd.*
import org.usvm.jvm.util.findFieldByFullNameOrNull
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestInstSerializer.Companion.registerUTestInstSerializer
import org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.Companion.registerUTestValueDescriptorSerializer
import org.usvm.test.api.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.UTestExceptionDescriptor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RdProcessRunner(
    private val process: Process,
    private val checkProcessAliveDelay: Duration = 1.seconds,
    private val rdPort: Int,
    private val jcClasspath: JcClasspath,
    private val lifetime: LifetimeDefinition
) {

    private val serializationContext = SerializationContext(jcClasspath)
    private val scheduler = SingleThreadScheduler(lifetime, "usvm-executor-scheduler")
    private val coroutineScope = UsvmRdCoroutineScope(lifetime, scheduler)
    private val traceDeserializer = TraceDeserializer(jcClasspath)
    lateinit var rdProcess: RdServerProcess


    init {
        lifetime.onTermination { process.destroyForcibly() }
    }

    suspend fun init() {
        rdProcess = initRdProcess()
    }

    private suspend fun initRdProcess(): RdServerProcess {
        val serializers = Serializers()
        serializers.registerUTestInstSerializer(serializationContext)
        serializers.registerUTestValueDescriptorSerializer(serializationContext)
        val protocol = Protocol(
            "usvm-executor",
            serializers,
            Identities(IdKind.Server),
            scheduler,
            SocketWire.Server(lifetime, scheduler, rdPort, "usvm-executor-socket"),
            lifetime
        )

        protocol.wire.connected.adviseForConditionAsync(lifetime).await()

        coroutineScope.launch(lifetime) {
            while (process.isAlive) {
                delay(checkProcessAliveDelay)
            }
            lifetime.terminate()
        }

        val model = protocol.scheduler.pumpAsync(lifetime) {
            protocol.syncProtocolModel
            protocol.instrumentedProcessModel
        }.await()


        protocol.syncProtocolModel.synchronizationSignal.let { sync ->
            val messageFromChild = sync.adviseForConditionAsync(lifetime) {
                it == CHILD_PROCESS_NAME
            }

            while (messageFromChild.isActive) {
                sync.fire(MAIN_PROCESS_NAME)
                delay(20.milliseconds)
            }
        }


        return RdServerProcess(process, lifetime, protocol, model)
    }

    private fun <TReq, Tres> RdCall<TReq, Tres>.fastSync(
        lifetime: Lifetime, request: TReq, timeout: Duration
    ): Tres {
        val task = start(lifetime, request, SynchronousScheduler)
        return task.wait(timeout.inWholeMilliseconds).unwrap()
    }

    private fun <T> IRdTask<T>.wait(timeoutMs: Long): RdTaskResult<T> {
        val future = CompletableFuture<RdTaskResult<T>>()
        result.advise(lifetime) {
            future.complete(it)
        }
        return future.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private suspend fun <T, R> RdCall<T, R>.execute(request: T): R =
        run {
            this@RdProcessRunner.serializationContext.reset()
            startSuspending(lifetime, request)
        }

    private fun <T, R> RdCall<T, R>.executeSync(request: T, timeout: Duration): R =
        run {
            this@RdProcessRunner.serializationContext.reset()
            fastSync(lifetime, request, timeout)
        }

    fun callUTestSync(uTest: UTest, timeout: Duration): UTestExecutionResult = try {
        val serializedUTest = SerializedUTest(uTest.initStatements, uTest.callMethodExpression)
        val serializedExecutionResult = rdProcess.model.callUTest.executeSync(serializedUTest, timeout)
        deserializeExecutionResult(serializedExecutionResult)
    } finally {
        serializationContext.reset()
    }

    suspend fun callUTestAsync(uTest: UTest): UTestExecutionResult =
        try {
            val serializedUTest = SerializedUTest(uTest.initStatements, uTest.callMethodExpression)
            val serializedExecutionResult = rdProcess.model.callUTest.execute(serializedUTest)
            deserializeExecutionResult(serializedExecutionResult)
        } finally {
            serializationContext.reset()
        }

    private fun deserializeExecutionResult(executionResult: ExecutionResult): UTestExecutionResult {
        val coveredClasses = executionResult.classes ?: listOf()
        return when (executionResult.type) {
            ExecutionResultType.UTestExecutionInitFailedResult -> UTestExecutionInitFailedResult(
                cause = executionResult.cause as? UTestExceptionDescriptor ?: error("deserialization failed"),
                trace = executionResult.trace?.let { deserializeTrace(it, coveredClasses) }
            )

            ExecutionResultType.UTestExecutionSuccessResult -> UTestExecutionSuccessResult(
                trace = executionResult.trace?.let { deserializeTrace(it, coveredClasses) },
                result = executionResult.result,
                initialState = executionResult.initialState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
                resultState = executionResult.resultState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
            )

            ExecutionResultType.UTestExecutionExceptionResult -> UTestExecutionExceptionResult(
                cause = executionResult.cause as? UTestExceptionDescriptor ?: error("deserialization failed"),
                trace = executionResult.trace?.let {
                    deserializeTrace(it, coveredClasses)
                },
                initialState = executionResult.initialState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
                resultState = executionResult.resultState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
            )

            ExecutionResultType.UTestExecutionFailedResult -> UTestExecutionFailedResult(
                cause = executionResult.cause as? UTestExceptionDescriptor ?: error("deserialization failed")
            )

            ExecutionResultType.UTestExecutionTimedOutResult -> UTestExecutionTimedOutResult(
                cause = executionResult.cause as? UTestExceptionDescriptor ?: error("deserialization failed")
            )
        }
    }

    private fun deserializeExecutionState(state: ExecutionStateSerialized): UTestExecutionState {
        val statics = state.statics?.associate {
            val jcField = jcClasspath.findFieldByFullNameOrNull(it.fieldName) ?: error("deserialization failed")
            val jcFieldDescriptor = it.fieldDescriptor
            jcField to jcFieldDescriptor
        } ?: mapOf()
        return UTestExecutionState(state.instanceDescriptor, state.argsDescriptors, statics.toMutableMap())
    }

    private fun deserializeTrace(trace: List<Long>, coveredClasses: List<ClassToId>): List<JcInst> =
        traceDeserializer.deserializeTrace(trace, coveredClasses)

    fun destroy() {
        lifetime.terminate()
    }

}