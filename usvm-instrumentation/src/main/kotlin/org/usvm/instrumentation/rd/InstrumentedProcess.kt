package org.usvm.instrumentation.rd

import CHILD_PROCESS_NAME
import MAIN_PROCESS_NAME
import adviseForConditionAsync
import awaitTermination
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.generated.models.*
import org.usvm.instrumentation.jacodb.transform.JcInstructionTracer
import org.usvm.instrumentation.jacodb.util.enclosingClass
import org.usvm.instrumentation.jacodb.util.enclosingMethod
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestExpressionSerializer.Companion.registerUTestExpressionSerializer
import org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.Companion.registerUTestValueDescriptorSerializer
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.UTestExecutor
import org.usvm.instrumentation.testcase.descriptor.DescriptorBuilder
import org.usvm.instrumentation.testcase.statement.*
import org.usvm.instrumentation.testcase.statement.ExecutionState
import org.usvm.instrumentation.trace.collector.TraceCollector
import pumpAsync
import terminateOnException
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class InstrumentedProcess private constructor() {

    private lateinit var jcClasspath: JcClasspath
    private lateinit var userClassLoader: WorkerClassLoader
    private lateinit var serializationCtx: SerializationContext
    private val traceCollector = JcInstructionTracer


    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val processInstance = InstrumentedProcess()
            processInstance.start(args)
        }
    }

    private enum class State {
        STARTED,
        ENDED
    }

    private val synchronizer = Channel<State>(capacity = 1)

    fun start(args: Array<String>) = runBlocking {
        val def = LifetimeDefinition()
        val timeout = 1.minutes // TODO! Parse from args
        val port = args.last().toInt()
        val classPath = args.first()
        initJcClasspath(classPath)
        def.terminateOnException {
            def.launch {
                checkAliveLoop(def, timeout)
            }

            initiate(def, port)

            def.awaitTermination()
        }
    }

    private suspend fun initJcClasspath(classpath: String) {
        val fileClassPath = classpath.split(':').map { File(it) }
        val db = jacodb {
            useProcessJavaRuntime()
            loadByteCode(fileClassPath)
            installFeatures(InMemoryHierarchy)
            //persistent(location = "/home/.usvm/jcdb.db", clearOnStart = false)
        }
        jcClasspath = db.classpath(fileClassPath)
        userClassLoader = WorkerClassLoader(
            fileClassPath.map { it.toURI().toURL() }.toTypedArray(),
            this::class.java.classLoader,
            TraceCollector::class.java.name,
            jcClasspath
        )
        serializationCtx = SerializationContext(jcClasspath)
    }

    private suspend fun initiate(lifetime: Lifetime, port: Int) {
        val scheduler = SingleThreadScheduler(lifetime, "usvm-executor-worker-scheduler")
        val serializers = Serializers()
        serializers.registerUTestExpressionSerializer(serializationCtx)
        serializers.registerUTestValueDescriptorSerializer(serializationCtx)
        val protocol = Protocol(
            "usvm-executor-worker",
            serializers,
            Identities(IdKind.Client),
            scheduler,
            SocketWire.Client(lifetime, scheduler, port),
            lifetime
        )

        val model = protocol.scheduler.pumpAsync(lifetime) {
            protocol.syncProtocolModel
            protocol.instrumentedProcessModel
        }.await()

        model.setup()

        protocol.syncProtocolModel.synchronizationSignal.let { sync ->
            val answerFromMainProcess = sync.adviseForConditionAsync(lifetime) {
                if (it == MAIN_PROCESS_NAME) {
                    measureExecutionForTermination {
                        sync.fire(CHILD_PROCESS_NAME)
                    }
                    true
                } else {
                    false
                }
            }
            answerFromMainProcess.await()
        }
    }

    private fun InstrumentedProcessModel.setup() {
        callUTest.measureExecutionForTermination { serializedUTest ->
            val uTest = UTest(serializedUTest.initStatements, serializedUTest.callMethodExpression as UTestCall)
            val callRes = callUTest(uTest)
            serializeExecutionResult(callRes)
        }
    }

    private fun serializeTrace(trace: List<JcInst>): List<SerializedTracedJcInst> = trace.map {
        SerializedTracedJcInst(
            className = it.enclosingClass.name,
            methodName = it.enclosingMethod.name,
            methodDescription = it.enclosingMethod.description,
            index = it.location.index
        )
    }

    private fun serializeExecutionResult(uTestExecutionResult: UTestExecutionResult): ExecutionResult =
        when (uTestExecutionResult) {
            is UTestExecutionExceptionResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionExceptionResult,
                cause = uTestExecutionResult.cause,
                trace = uTestExecutionResult.trace?.let { serializeTrace(it) },
                initialState = null,
                result = null,
                resultState = null
            )

            is UTestExecutionFailedResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionFailedResult,
                cause = uTestExecutionResult.cause,
                trace = null,
                initialState = null,
                result = null,
                resultState = null
            )

            is UTestExecutionInitFailedResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionInitFailedResult,
                cause = uTestExecutionResult.cause,
                trace = uTestExecutionResult.trace?.let { serializeTrace(it) },
                initialState = null,
                result = null,
                resultState = null
            )

            is UTestExecutionSuccessResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionSuccessResult,
                trace = uTestExecutionResult.trace?.let { serializeTrace(it) },
                initialState = serializeExecutionState(uTestExecutionResult.initialState),
                result = uTestExecutionResult.result,
                resultState = serializeExecutionState(uTestExecutionResult.resultState),
                cause = null
            )

            is UTestExecutionTimedOutResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionTimedOutResult,
                cause = uTestExecutionResult.cause,
                trace = null,
                initialState = null,
                result = null,
                resultState = null
            )
        }

    private fun serializeExecutionState(executionState: ExecutionState): ExecutionStateSerialized =
        ExecutionStateSerialized(executionState.instanceDescriptor, executionState.argsDescriptors)

    private fun callUTest(uTest: UTest): UTestExecutionResult {
        traceCollector.reset()
        val callMethodExpr = uTest.callMethodExpression as UTestMethodCall
        val executor = UTestExecutor(userClassLoader)
        executor.executeUTestExpressions(uTest.initStatements)
            ?.onFailure { return UTestExecutionInitFailedResult(it.message ?: "", traceCollector.getTrace()) }
        val initStateDescriptorBuilder = DescriptorBuilder(userClassLoader, null)
        val initExecutionState = buildExecutionState(callMethodExpr, executor, initStateDescriptorBuilder)
        val methodInvocationResult =
            executor.executeUTestExpression(callMethodExpr)
                .onFailure { return UTestExecutionExceptionResult(it.message ?: "", traceCollector.getTrace()) }
                .getOrNull()
        val resultStateDescriptorBuilder = DescriptorBuilder(userClassLoader, initStateDescriptorBuilder)
        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder
                .buildDescriptorResultFromAny(methodInvocationResult)
                .getOrNull()
        val resultExecutionState = buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder)
        return UTestExecutionSuccessResult(
            traceCollector.getTrace(),
            methodInvocationResultDescriptor,
            initExecutionState,
            resultExecutionState
        )
    }

    private fun buildExecutionState(callMethodExpr: UTestMethodCall, executor: UTestExecutor, descriptorBuilder: DescriptorBuilder): ExecutionState {
        val instanceDescriptor =
            descriptorBuilder.buildDescriptorFromUTestExpr(callMethodExpr.instance, executor)?.getOrNull()
        val argsDescriptors = callMethodExpr.args.map {
            descriptorBuilder.buildDescriptorFromUTestExpr(it, executor)?.getOrNull()
        }
        return ExecutionState(instanceDescriptor, argsDescriptors)
    }

    private inline fun <T> measureExecutionForTermination(block: () -> T): T {
        try {
            synchronizer.trySendBlocking(State.STARTED).exceptionOrNull()
            return block()
        } finally {
            synchronizer.trySendBlocking(State.ENDED).exceptionOrNull()
        }
    }

    fun <T, R> RdCall<T, R>.measureExecutionForTermination(block: (T) -> R) {
        set { request ->
            try {
                serializationCtx.reset()
                measureExecutionForTermination<R> {
                    block(request)
                }
            } finally {
                serializationCtx.reset()
            }
        }
    }

    private suspend fun checkAliveLoop(lifetime: LifetimeDefinition, timeout: Duration) {
        var lastState = State.ENDED
        while (true) {
            val current = withTimeoutOrNull(timeout) {
                synchronizer.receive()
            }

            if (current == null) {
                if (lastState == State.ENDED) {
                    lifetime.terminate()
                    break
                }
            } else {
                lastState = current
            }
        }
    }

    private fun printTrace(trace: List<JcInst>) =
        println(buildString {
            var offset = 2
            var curMethod = trace.first().location.method
            for (i in 0 until trace.size - 1) {
                val jcInst = trace[i]
                if (jcInst.location.method != curMethod) {
                    offset -= 2
                    curMethod = jcInst.location.method
                }
                repeat(offset) { append("|") }
                append(" ")
                appendLine(jcInst)
                val callExpr = jcInst.operands.find { it is JcCallExpr } as? JcCallExpr
                if (callExpr != null || jcInst is JcCallInst) {
                    offset += 2; curMethod = trace[i + 1].location.method
                }
            }
        })


}