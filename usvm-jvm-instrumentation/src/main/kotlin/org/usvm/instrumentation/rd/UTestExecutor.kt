package org.usvm.instrumentation.rd

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.*
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.URLClassPathLoader
import java.lang.Exception

class UTestExecutor(
    private val jcClasspath: JcClasspath,
    private val ucp: URLClassPathLoader
) {

    private var workerClassLoader = createWorkerClassLoader()
    private var initStateDescriptorBuilder = Value2DescriptorConverter(
        workerClassLoader = workerClassLoader,
        previousState = null
    )
    private var staticDescriptorsBuilder = StaticDescriptorsBuilder(
        workerClassLoader = workerClassLoader,
        initialValue2DescriptorConverter = initStateDescriptorBuilder
    )
    private var mockHelper = MockHelper(
        jcClasspath = jcClasspath,
        classLoader = workerClassLoader
    )

    init {
        workerClassLoader.setStaticDescriptorsBuilder(staticDescriptorsBuilder)
    }

    private fun createWorkerClassLoader() =
        WorkerClassLoader(
            urlClassPath = ucp,
            traceCollectorClassLoader = this::class.java.classLoader,
            traceCollectorClassName = TraceCollector::class.java.name,
            mockCollectorClassName = MockCollector::class.java.name,
            jcClasspath = jcClasspath
        )

    private fun reset() {
        initStateDescriptorBuilder = Value2DescriptorConverter(
            workerClassLoader = workerClassLoader,
            previousState = null
        )
        staticDescriptorsBuilder = StaticDescriptorsBuilder(
            workerClassLoader = workerClassLoader,
            initialValue2DescriptorConverter = initStateDescriptorBuilder
        )
        JcInstructionTracer.reset()
        MockCollector.mocks.clear()
    }

    fun executeUTest(uTest: UTest): UTestExecutionResult {
        when (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy) {
            StaticsRollbackStrategy.HARD -> workerClassLoader = createWorkerClassLoader()
            else -> {}
        }
        reset()
        val accessedStatics = mutableSetOf<Pair<JcField, JcInstructionTracer.FieldAccessType>>()
        val callMethodExpr = uTest.callMethodExpression

        val executor = UTestExpressionExecutor(workerClassLoader, accessedStatics, mockHelper)
        val initStmts = (uTest.initStatements + listOf(callMethodExpr.instance) + callMethodExpr.args).filterNotNull()
        executor.executeUTestInsts(initStmts)
            ?.onFailure {
                return UTestExecutionInitFailedResult(
                    cause = buildExceptionDescriptor(
                        builder = initStateDescriptorBuilder,
                        exception = it,
                        raisedByUserCode = false
                    ),
                    trace = mapOf()//JcInstructionTracer.getTrace().trace
                )
            }

        val initExecutionState = buildExecutionState(
            callMethodExpr = callMethodExpr,
            executor = executor,
            descriptorBuilder = initStateDescriptorBuilder,
            accessedStatics = hashSetOf(),
            accessedFields = listOf()
        )

        TraceCollector.trace.clear()
        val methodInvocationResult =
            executor.executeUTestInst(callMethodExpr)
        val resultStateDescriptorBuilder =
            Value2DescriptorConverter(workerClassLoader, initStateDescriptorBuilder)
        val unpackedInvocationResult =
            when {
                methodInvocationResult.isFailure -> methodInvocationResult.exceptionOrNull()
                else -> methodInvocationResult.getOrNull()
            }

        val trace = JcInstructionTracer.getTrace()
        val accessedFields = trace.fields.map { it.first }
        accessedStatics.addAll(trace.statics.toSet())

        if (unpackedInvocationResult is Throwable) {
            val resultExecutionState =
                buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics, accessedFields)
            return UTestExecutionExceptionResult(
                cause = buildExceptionDescriptor(
                    builder = resultStateDescriptorBuilder,
                    exception = unpackedInvocationResult,
                    raisedByUserCode = methodInvocationResult.isSuccess
                ),
                trace = JcInstructionTracer.getTrace().trace,
                initialState = initExecutionState,
                resultState = resultExecutionState
            )
        }

        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder.buildDescriptorResultFromAny(unpackedInvocationResult, callMethodExpr.type)
                .getOrNull()
        val resultExecutionState =
            buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics, accessedFields)

        val accessedStaticsFields = accessedStatics.map { it.first }
        val staticsToRemoveFromInitState = initExecutionState.statics.keys.filter { it !in accessedStaticsFields }
        staticsToRemoveFromInitState.forEach { initExecutionState.statics.remove(it) }
        if (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy == StaticsRollbackStrategy.ROLLBACK) {
            staticDescriptorsBuilder.rollBackStatics()
        } else if (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy == StaticsRollbackStrategy.REINIT) {
            workerClassLoader.reset(accessedStaticsFields)
        }


        return UTestExecutionSuccessResult(
            trace.trace, methodInvocationResultDescriptor, initExecutionState, resultExecutionState
        )
    }

    private fun buildExceptionDescriptor(
        builder: Value2DescriptorConverter,
        exception: Throwable,
        raisedByUserCode: Boolean
    ): UTestExceptionDescriptor {
        val descriptor =
            builder.buildDescriptorResultFromAny(any = exception, type = null).getOrNull() as? UTestExceptionDescriptor
        return descriptor
            ?.also { it.raisedByUserCode = raisedByUserCode }
            ?: UTestExceptionDescriptor(
                type = jcClasspath.findClassOrNull(exception::class.java.name)?.toType()
                    ?: jcClasspath.findClass<Exception>().toType(),
                message = exception.message ?: "message_is_null",
                stackTrace = listOf(),
                raisedByUserCode = raisedByUserCode
            )
    }

    private fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, JcInstructionTracer.FieldAccessType>>,
        accessedFields: List<JcField>
    ): UTestExecutionState = with(descriptorBuilder) {
        uTestExecutorCache.addAll(executor.objectToInstructionsCache)
        val instanceDescriptor = callMethodExpr.instance?.let {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val argsDescriptors = callMethodExpr.args.map {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val isInit = previousState == null
        val statics = if (isInit) {
            staticDescriptorsBuilder.builtInitialDescriptors
                .mapValues { it.value!! }
        } else {
            staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(accessedStatics, descriptorBuilder).getOrThrow()
        }
        return UTestExecutionState(instanceDescriptor, argsDescriptors, statics.toMutableMap(), accessedFields)
    }
}