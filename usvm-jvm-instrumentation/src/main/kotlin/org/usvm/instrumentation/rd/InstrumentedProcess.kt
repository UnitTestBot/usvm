package org.usvm.instrumentation.rd

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
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.generated.models.*
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestInstSerializer.Companion.registerUTestInstSerializer
import org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.Companion.registerUTestValueDescriptorSerializer
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.*
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

    private lateinit var uTestExecutor: UTestExecutor

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
            addOption("cp", true, "Project class path")
            addOption("t", true, "Process timeout in seconds")
            addOption("p", true, "Rd port number")
            addOption("javahome", true, "java home dir")
            addOption("persistence", true, "Jacodb persistence location")
        }
        val parser = DefaultParser()
        val cmd = parser.parse(options, args)
        val classPath = cmd.getOptionValue("cp") ?: error("Specify classpath")
        val timeout = cmd.getOptionValue("t").toIntOrNull()?.toDuration(DurationUnit.SECONDS)
            ?: error("Specify timeout in seconds")
        val port = cmd.getOptionValue("p").toIntOrNull() ?: error("Specify rd port number")
        val javaHome = cmd.getOptionValue("javahome") ?: error("Specify java home")
        val persistentLocation = cmd.getOptionValue("persistence")

        val def = LifetimeDefinition()
        initProcess(classPath, javaHome, persistentLocation)
        def.terminateOnException {
            def.launch {
                checkAliveLoop(def, timeout)
            }

            initiate(def, port)

            def.awaitTermination()
        }
    }

    private suspend fun initProcess(classpath: String, javaHome: String, persistentLocation: String?) {
        fileClassPath = classpath.split(File.pathSeparatorChar).map { File(it) }
        val db = jacodb {
            loadByteCode(fileClassPath)
            installFeatures(InMemoryHierarchy)
            jre = File(javaHome)
            if (persistentLocation != null) {
                persistent(location = persistentLocation, clearOnStart = false)
            }
        }
        jcClasspath = db.classpath(fileClassPath)
        serializationCtx = SerializationContext(jcClasspath)
        ucp = URLClassPathLoader(fileClassPath)
        uTestExecutor = UTestExecutor(jcClasspath, ucp)
    }

    private suspend fun initiate(lifetime: Lifetime, port: Int) {
        val scheduler = SingleThreadScheduler(lifetime, "usvm-executor-worker-scheduler")
        val serializers = Serializers()
        serializers.registerUTestInstSerializer(serializationCtx)
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

    private fun serializeExecutionResult(uTestExecutionResult: UTestExecutionResult): ExecutionResult {
        val classesToId = JcInstructionTracer.getEncodedClasses().entries.map { ClassToId(it.key.name, it.value) }
        return when (uTestExecutionResult) {
            is UTestExecutionExceptionResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionExceptionResult,
                classes = classesToId,
                cause = uTestExecutionResult.cause,
                trace = JcInstructionTracer.coveredInstructionsIds(),
                initialState = serializeExecutionState(uTestExecutionResult.initialState),
                result = null,
                resultState = serializeExecutionState(uTestExecutionResult.resultState),
            )

            is UTestExecutionFailedResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionFailedResult,
                classes = classesToId,
                cause = uTestExecutionResult.cause,
                trace = null,
                initialState = null,
                result = null,
                resultState = null
            )

            is UTestExecutionInitFailedResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionInitFailedResult,
                classes = classesToId,
                cause = uTestExecutionResult.cause,
                trace = JcInstructionTracer.coveredInstructionsIds(),
                initialState = null,
                result = null,
                resultState = null
            )

            is UTestExecutionSuccessResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionSuccessResult,
                classes = classesToId,
                trace = JcInstructionTracer.coveredInstructionsIds(),
                initialState = serializeExecutionState(uTestExecutionResult.initialState),
                result = uTestExecutionResult.result,
                resultState = serializeExecutionState(uTestExecutionResult.resultState),
                cause = null
            )
            //TODO Return initial state with timeouts
            is UTestExecutionTimedOutResult -> ExecutionResult(
                type = ExecutionResultType.UTestExecutionTimedOutResult,
                classes = classesToId,
                cause = uTestExecutionResult.cause,
                trace = null,
                initialState = null,
                result = null,
                resultState = null
            )
        }
    }

    private fun serializeExecutionState(executionState: UTestExecutionState): ExecutionStateSerialized {
        val statics = executionState.statics.entries.map { (jcField, descriptor) ->
            SerializedStaticField("${jcField.enclosingClass.name}.${jcField.name}", descriptor)
        }
        return ExecutionStateSerialized(executionState.instanceDescriptor, executionState.argsDescriptors, statics)
    }

    private fun callUTest(uTest: UTest): UTestExecutionResult =
        uTestExecutor.executeUTest(uTest)

    private inline fun <T> measureExecutionForTermination(block: () -> T): T {
        try {
            synchronizer.trySendBlocking(State.STARTED).exceptionOrNull()
            return block()
        } finally {
            synchronizer.trySendBlocking(State.ENDED).exceptionOrNull()
        }
    }

    private fun <T, R> RdCall<T, R>.measureExecutionForTermination(block: (T) -> R) {
        set { request ->
            try {
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