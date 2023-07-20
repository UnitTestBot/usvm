package org.usvm.instrumentation.org.usvm.instrumentation.rd

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.rd.StaticsRollbackStrategy
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.URLClassPathLoader

class UTestExecutor(
    private val jcClasspath: JcClasspath,
    private val ucp: URLClassPathLoader
) {

    private var workerClassLoader = createWorkerClassLoader()
    private var initStateDescriptorBuilder = Value2DescriptorConverter(workerClassLoader, null)
    private var staticDescriptorsBuilder = StaticDescriptorsBuilder(workerClassLoader, initStateDescriptorBuilder)
    private var mockHelper = MockHelper(jcClasspath, workerClassLoader)

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

    fun executeUTest(uTest: UTest): UTestExecutionResult {
        when (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy) {
            StaticsRollbackStrategy.HARD -> workerClassLoader = createWorkerClassLoader()
            else -> {}
        }
        JcInstructionTracer.reset()
        MockCollector.mocks.clear()

        val accessedStatics = mutableSetOf<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>()
        val callMethodExpr = uTest.callMethodExpression
        val executor = UTestExpressionExecutor(workerClassLoader, accessedStatics, mockHelper)
        executor.executeUTestExpressions(uTest.initStatements)
            ?.onFailure { return UTestExecutionInitFailedResult(it.message ?: "", JcInstructionTracer.getTrace().trace) }

        val initExecutionState = buildExecutionState(
            callMethodExpr, executor, initStateDescriptorBuilder, hashSetOf()
        )

        executor.executeUTestExpressions(uTest.initStatements)
            ?.onFailure { return UTestExecutionInitFailedResult(it.message ?: "", JcInstructionTracer.getTrace().trace) }

        val methodInvocationResult =
            executor.executeUTestExpression(callMethodExpr).onFailure {
                return UTestExecutionExceptionResult(
                    "$it\n${it.stackTraceToString()}",
                    JcInstructionTracer.getTrace().trace,
                    initExecutionState,
                    //TODO!!! decide if should we build resulting state
                    initExecutionState
                )
            }.getOrNull()
        val resultStateDescriptorBuilder =
            Value2DescriptorConverter(workerClassLoader, initStateDescriptorBuilder)
        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder.buildDescriptorResultFromAny(methodInvocationResult).getOrNull()
        val trace = JcInstructionTracer.getTrace()
        accessedStatics.addAll(trace.statics.toSet())
        val resultExecutionState =
            buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics)

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

    private fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>
    ): UTestExecutionState = with(descriptorBuilder) {
        val instanceDescriptor = callMethodExpr.instance?.let {
            buildDescriptorFromUTestExpr(it, executor)?.getOrNull()
        }
        val argsDescriptors = callMethodExpr.args.map {
            buildDescriptorFromUTestExpr(it, executor)?.getOrNull()
        }
        executor.clearCache()
        val isInit = descriptorBuilder.previousState == null
        val statics = if (isInit) {
            staticDescriptorsBuilder.builtInitialDescriptors.mapValues { it.value!! }
        } else {
            staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(accessedStatics, descriptorBuilder).getOrThrow()
        }
        return UTestExecutionState(instanceDescriptor, argsDescriptors, statics.toMutableMap())
    }
}