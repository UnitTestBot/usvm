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
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.classloader.*
import org.usvm.instrumentation.generated.models.*
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.org.usvm.instrumentation.classloader.MockHelper
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestExpressionSerializer.Companion.registerUTestExpressionSerializer
import org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.Companion.registerUTestValueDescriptorSerializer
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.trace.collector.MockCollector
import org.usvm.instrumentation.trace.collector.TraceCollector
import org.usvm.instrumentation.util.*
import pumpAsync
import terminateOnException
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

//Main class for worker process
class InstrumentedProcess private constructor() {

    private lateinit var jcClasspath: JcClasspath
    private lateinit var serializationCtx: SerializationContext
    private lateinit var fileClassPath: List<File>
    private lateinit var ucp: URLClassPathLoader

    private lateinit var staticDescriptorsBuilder: StaticDescriptorsBuilder
    private lateinit var initStateDescriptorBuilder: Value2DescriptorConverter
    private lateinit var userClassLoader: WorkerClassLoader
    private lateinit var mockHelper: MockHelper

    private val traceCollector = JcInstructionTracer

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val processInstance = InstrumentedProcess()
            processInstance.start(args)
        }
    }

    private enum class State {
        STARTED, ENDED
    }

    private val synchronizer = Channel<State>(capacity = 1)

    fun start(args: Array<String>) = runBlocking {
        val options = Options()
        with(options) {
            addOption("cp",true, "Project class path")
            addOption("t",true, "Process timeout in seconds")
            addOption("p",true, "Rd port number")
        }
        val parser = DefaultParser()
        val cmd = parser.parse(options, args)
        val classPath = cmd.getOptionValue("cp") ?: error("Specify classpath")
        val timeout = cmd.getOptionValue("t").toIntOrNull()?.toDuration(DurationUnit.SECONDS) ?: error("Specify timeout in seconds")
        val port = cmd.getOptionValue("p").toIntOrNull() ?: error("Specify rd port number")
        val def = LifetimeDefinition()
        initProcess(classPath)
        def.terminateOnException {
            def.launch {
                checkAliveLoop(def, timeout)
            }

            initiate(def, port)

            def.awaitTermination()
        }
    }

    private suspend fun initProcess(classpath: String) {
        fileClassPath = classpath.split(':').map { File(it) }
        val db = jacodb {
            loadByteCode(fileClassPath)
            installFeatures(InMemoryHierarchy)
            //persistent(location = "/home/.usvm/jcdb.db", clearOnStart = false)
        }
        jcClasspath = db.asyncClasspath(fileClassPath).get()
        serializationCtx = SerializationContext(jcClasspath)
        ucp = URLClassPathLoader(fileClassPath)
        userClassLoader = createWorkerClassLoader()
        initStateDescriptorBuilder = Value2DescriptorConverter(userClassLoader, null)
        staticDescriptorsBuilder = StaticDescriptorsBuilder(userClassLoader, initStateDescriptorBuilder)
        userClassLoader.setStaticDescriptorsBuilder(staticDescriptorsBuilder)
        mockHelper = MockHelper(jcClasspath, userClassLoader)
    }

    private fun createWorkerClassLoader() =
        WorkerClassLoader(ucp, this::class.java.classLoader, TraceCollector::class.java.name, MockCollector::class.java.name, jcClasspath)

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

    private fun serializeExecutionState(executionState: UTestExecutionState): ExecutionStateSerialized {
        val statics = executionState.statics.entries.map { (jcField, descriptor) ->
            SerializedStaticField("${jcField.enclosingClass.name}.${jcField.name}", descriptor)
        }
        return ExecutionStateSerialized(executionState.instanceDescriptor, executionState.argsDescriptors, statics)
    }

    private fun callUTest(uTest: UTest): UTestExecutionResult {
        when (InstrumentationModuleConstants.rollbackStrategy) {
            StaticsRollbackStrategy.HARD -> userClassLoader = createWorkerClassLoader()
            else -> {}
        }
        traceCollector.reset()
        MockCollector.mocks.clear()

        val accessedStatics = mutableSetOf<Pair<JcField, StaticFieldAccessType>>()
        val callMethodExpr = uTest.callMethodExpression
        val executor = UTestExpressionExecutor(userClassLoader, accessedStatics, mockHelper)

        executor.executeUTestExpressions(uTest.initStatements)
            ?.onFailure { return UTestExecutionInitFailedResult(it.message ?: "", traceCollector.getTrace().trace) }

        val initExecutionState = buildExecutionState(
            callMethodExpr, executor, initStateDescriptorBuilder, hashSetOf()
        )
        val methodInvocationResult =
            executor.executeUTestExpression(callMethodExpr).onFailure {
                return UTestExecutionExceptionResult(
                    "$it\n${it.stackTraceToString()}", traceCollector.getTrace().trace
                )
            }.getOrNull()

        val resultStateDescriptorBuilder =
            Value2DescriptorConverter(userClassLoader, initStateDescriptorBuilder)
        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder.buildDescriptorResultFromAny(methodInvocationResult).getOrNull()
        val trace = traceCollector.getTrace()
        accessedStatics.addAll(trace.statics.toSet())
        val resultExecutionState =
            buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics)

        val accessedStaticsFields = accessedStatics.map { it.first }
        val staticsToRemoveFromInitState = initExecutionState.statics.keys.filter { it !in accessedStaticsFields }
        staticsToRemoveFromInitState.forEach { initExecutionState.statics.remove(it) }
        if (InstrumentationModuleConstants.rollbackStrategy == StaticsRollbackStrategy.ROLLBACK) {
            staticDescriptorsBuilder.rollBackStatics()
        } else if (InstrumentationModuleConstants.rollbackStrategy == StaticsRollbackStrategy.REINIT) {
            userClassLoader.reset(accessedStaticsFields)
        }

        return UTestExecutionSuccessResult(
            trace.trace, methodInvocationResultDescriptor, initExecutionState, resultExecutionState
        )
    }

    private fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, StaticFieldAccessType>>
    ): UTestExecutionState = with(descriptorBuilder) {
        val instanceDescriptor = if (callMethodExpr.instance != null) {
            buildDescriptorFromUTestExpr(callMethodExpr.instance!!, executor)?.getOrNull()
        } else null
        val argsDescriptors = callMethodExpr.args.map {
            buildDescriptorFromUTestExpr(it, executor)?.getOrNull()
        }
        val isInit = descriptorBuilder.previousState == null
        val statics = if (isInit) {
            staticDescriptorsBuilder.builtInitialDescriptors.mapValues { it.value!! }
        } else {
            staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(accessedStatics, descriptorBuilder).getOrThrow()
        }
        return UTestExecutionState(instanceDescriptor, argsDescriptors, statics.toMutableMap())
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

}

enum class StaticsRollbackStrategy {
    REINIT,     // Calls <clinit> method
    ROLLBACK,   // Performs manual static rollback (if possible)
    HARD        // Create new classloader (slow, but, reliable)
}