package org.usvm.instrumentation.executor

import CHILD_PROCESS_NAME
import MAIN_PROCESS_NAME
import adviseForConditionAsync
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlinx.coroutines.delay
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.usvm.instrumentation.generated.models.*
import org.usvm.instrumentation.util.findFieldByFullNameOrNull
import org.usvm.instrumentation.rd.RdServerProcess
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestExpressionSerializer.Companion.registerUTestExpressionSerializer
import org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.Companion.registerUTestValueDescriptorSerializer
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import pumpAsync
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
    lateinit var rdProcess: RdServerProcess


    init {
        lifetime.onTermination { process.destroyForcibly() }
    }

    suspend fun init() {
        rdProcess = initRdProcess()
    }

    private suspend fun initRdProcess(): RdServerProcess {
        val serializers = Serializers()
        serializers.registerUTestExpressionSerializer(serializationContext)
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
        try {
            this@RdProcessRunner.serializationContext.reset()
            startSuspending(lifetime, request)
        } finally {
            this@RdProcessRunner.serializationContext.reset()
        }

    private fun <T, R> RdCall<T, R>.executeSync(request: T, timeout: Duration): R =
        try {
            this@RdProcessRunner.serializationContext.reset()
            fastSync(lifetime, request, timeout)
        } finally {
            this@RdProcessRunner.serializationContext.reset()
        }

    fun callUTestSync(uTest: UTest, timeout: Duration): UTestExecutionResult {
        val serializedUTest = SerializedUTest(uTest.initStatements, uTest.callMethodExpression)
        val serializedExecutionResult = rdProcess.model.callUTest.executeSync(serializedUTest, timeout)
        return deserializeExecutionResult(serializedExecutionResult)
    }

    suspend fun callUTestAsync(uTest: UTest): UTestExecutionResult {
        val serializedUTest = SerializedUTest(uTest.initStatements, uTest.callMethodExpression)
        val serializedExecutionResult = rdProcess.model.callUTest.execute(serializedUTest)
        return deserializeExecutionResult(serializedExecutionResult)
    }

    private fun deserializeExecutionResult(executionResult: ExecutionResult): UTestExecutionResult =
        when (executionResult.type) {
            ExecutionResultType.UTestExecutionInitFailedResult -> UTestExecutionInitFailedResult(
                cause = executionResult.cause ?: error("deserialization failed"),
                trace = executionResult.trace?.let { deserializeTrace(it) }
            )

            ExecutionResultType.UTestExecutionSuccessResult -> UTestExecutionSuccessResult(
                trace = executionResult.trace?.let { deserializeTrace(it) },
                result = executionResult.result,
                initialState = executionResult.initialState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
                resultState = executionResult.resultState?.let { deserializeExecutionState(it) }
                    ?: error("deserialization failed"),
            )

            ExecutionResultType.UTestExecutionExceptionResult -> UTestExecutionExceptionResult(
                cause = executionResult.cause ?: error("deserialization failed"),
                trace = executionResult.trace?.let { deserializeTrace(it) }
            )

            ExecutionResultType.UTestExecutionFailedResult -> UTestExecutionFailedResult(
                cause = executionResult.cause ?: error("deserialization failed")
            )

            ExecutionResultType.UTestExecutionTimedOutResult -> UTestExecutionTimedOutResult(
                cause = executionResult.cause ?: error("deserialization failed")
            )
        }

    private fun deserializeExecutionState(state: ExecutionStateSerialized): UTestExecutionState {
        val statics = state.statics?.associate {
            val jcField = jcClasspath.findFieldByFullNameOrNull(it.fieldName) ?: error("deserialization failed")
            val jcFieldDescriptor = it.fieldDescriptor
            jcField to jcFieldDescriptor
        } ?: mapOf()
        return UTestExecutionState(state.instanceDescriptor, state.argsDescriptors, statics.toMutableMap())
    }

    private fun deserializeTrace(trace: List<SerializedTracedJcInst>): List<JcInst> = trace.map { serInst ->
        val method = jcClasspath
            .findClass(serInst.className)
            .findMethodOrNull(serInst.methodName, serInst.methodDescription)
            ?: error("deserialization failed")
        method.instList
            .find { it.location.index == serInst.index }
            ?: error("deserialization failed")
    }

    fun destroy() {
        lifetime.terminate()
    }

}