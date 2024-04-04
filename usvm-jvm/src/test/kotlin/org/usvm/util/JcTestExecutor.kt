package org.usvm.util

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.LocationType
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.toType
import org.jacodb.approximation.JcEnrichedVirtualField
import org.jacodb.impl.fs.BuildFolderLocation
import org.jacodb.impl.fs.JarLocation
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockSymbol
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.StaticFieldValue
import org.usvm.api.util.JcTestResolver
import org.usvm.api.util.JcTestStateResolver
import org.usvm.collection.field.UFieldLValue
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionFailedResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestMockObject
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.machine.JcContext
import org.usvm.machine.JcMocker
import org.usvm.machine.state.JcState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses concrete execution
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestExecutor(
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    val classpath: JcClasspath,
) : JcTestResolver {

    private val runner: UTestConcreteExecutor
        get() {
            if (!UTestRunner.isInitialized()) {
                val pathToJars =
                    classpath.locations
                        .filter { it is BuildFolderLocation || (it is JarLocation && it.type == LocationType.APP) }
                        .map { it.path }
                UTestRunner.initRunner(pathToJars, classpath)
            }
            return UTestRunner.runner
        }

    private val descriptor2ValueConverter = Descriptor2ValueConverter(classLoader)

    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    override fun resolve(
        method: JcTypedMethod,
        state: JcState,
    ): JcTest {
        val model = state.models.first()

        val ctx = state.ctx

        val mocker = state.memory.mocker as JcMocker
//        val staticMethodMocks = mocker.statics TODO global mocks?????????????????????????
        val methodMocks = mocker.symbols

        val resolvedMethodMocks = methodMocks.entries.groupBy({ model.eval(it.key) }, { it.value })
            .mapValues { it.value.flatten() }

        val memoryScope = MemoryScope(ctx, model, state.memory, method, resolvedMethodMocks)

        val before: JcParametersState
        val after: JcParametersState
        val uTest = memoryScope.createUTest()

        val execResult = runBlocking {
            runner.executeAsync(uTest)
        }
        descriptor2ValueConverter.clear()
        val result =
            when (execResult) {
                is UTestExecutionSuccessResult -> {
                    val thisBeforeDescr = execResult.initialState.instanceDescriptor
                    val thisBefore = thisBeforeDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val beforeArgsDescr = execResult.initialState.argsDescriptors.map { it }
                    val argsBefore = beforeArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }

                    val staticsBefore = execResult
                        .initialState
                        .statics.map { it.key to descriptor2ValueConverter.buildObjectFromDescriptor(it.value) }
                        .groupBy(keySelector = { it.first.enclosingClass }) { StaticFieldValue(it.first, it.second) }

                    before = JcParametersState(thisBefore, argsBefore, staticsBefore)

                    val thisAfterDescr = execResult.resultState.instanceDescriptor
                    val thisAfter = thisAfterDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val afterArgsDescr = execResult.resultState.argsDescriptors.map { it }
                    val argsAfter = afterArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }

                    val staticsAfter = execResult
                        .resultState
                        .statics.map { it.key to descriptor2ValueConverter.buildObjectFromDescriptor(it.value) }
                        .groupBy(keySelector = { it.first.enclosingClass }) { StaticFieldValue(it.first, it.second) }

                    after = JcParametersState(thisAfter, argsAfter, staticsAfter)

                    Result.success(execResult.result?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) })
                }

                is UTestExecutionExceptionResult -> {
                    val exceptionInstance =
                        descriptor2ValueConverter.buildObjectFromDescriptor(execResult.cause) as? Throwable
                            ?: error("Exception building error")
                    val thisBeforeDescr = execResult.initialState.instanceDescriptor
                    val thisBefore = thisBeforeDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val beforeArgsDescr = execResult.initialState.argsDescriptors.map { it }
                    val argsBefore = beforeArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }
                    val staticsBefore = execResult
                        .initialState
                        .statics.map { it.key to descriptor2ValueConverter.buildObjectFromDescriptor(it.value) }
                        .groupBy(keySelector = { it.first.enclosingClass }) { StaticFieldValue(it.first, it.second) }


                    before = JcParametersState(thisBefore, argsBefore, staticsBefore)

                    val thisAfterDescr = execResult.resultState.instanceDescriptor
                    val thisAfter = thisAfterDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val afterArgsDescr = execResult.resultState.argsDescriptors.map { it }
                    val argsAfter = afterArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }
                    val staticsAfter = execResult
                        .resultState
                        .statics.map { it.key to descriptor2ValueConverter.buildObjectFromDescriptor(it.value) }
                        .groupBy(keySelector = { it.first.enclosingClass }) { StaticFieldValue(it.first, it.second) }

                    after = JcParametersState(thisAfter, argsAfter, staticsAfter)
                    if (execResult.cause.raisedByUserCode) {
                        Result.success(exceptionInstance)
                    } else {
                        Result.failure(exceptionInstance)
                    }
                }

                is UTestExecutionFailedResult -> {
                    val exceptionInstance =
                        descriptor2ValueConverter.buildObjectFromDescriptor(execResult.cause) as? Throwable
                            ?: error("Exception building error")
                    before = emptyJcParametersState()
                    after = emptyJcParametersState()
                    if (execResult.cause.raisedByUserCode) {
                        Result.success(exceptionInstance)
                    } else {
                        Result.failure(exceptionInstance)
                    }
                }

                else -> {
                    error("No result")
                }
            }

        val coverage = resolveCoverage(method, state)

        return JcTest(method, before, after, result, coverage)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    private fun emptyJcParametersState() = JcParametersState(null, listOf(), emptyMap())

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param finalStateMemory a read-only memory to read [ULValue]s from.
     */
    private class MemoryScope(
        ctx: JcContext,
        model: UModelBase<JcType>,
        finalStateMemory: UReadOnlyMemory<JcType>,
        method: JcTypedMethod,
        val resolvedMethodMocks: Map<UHeapRef, List<UMockSymbol<*>>>,
    ) : JcTestStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {

        override val decoderApi = JcTestExecutorDecoderApi(ctx)

        fun createUTest(): UTest {
            val thisInstance = resolveThisInstance()
            val parameters = resolveParameters()

            resolveStatics()

            val initStmts = decoderApi.initializerInstructions()

            val callExpr = if (method.isStatic) {
                UTestStaticMethodCall(method.method, parameters)
            } else {
                UTestMethodCall(thisInstance, method.method, parameters)
            }

            return UTest(initStmts, callExpr)
        }

        override fun allocateClassInstance(type: JcClassType): UTestExpression =
            UTestAllocateMemoryCall(type.jcClass)

        override fun allocateString(value: UTestExpression): UTestExpression {
            val stringConstructor = ctx.stringType.constructors
                .firstOrNull { it.parameters.size == 1 && it.parameters.single().type == value.type }
                ?: error("Can't allocate string from value: $value")
            return decoderApi.invokeMethod(stringConstructor.method, listOf(value))
        }

        override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): UTestExpression {
            if (ref !in resolvedMethodMocks) {
                return super.resolveObject(ref, heapRef, type)
            }

            val mocks = resolvedMethodMocks.getValue(ref)

            val fieldValues = mutableMapOf<JcField, UTestExpression>()
            val methods = mutableMapOf<JcMethod, List<UTestExpression>>()

            val instance = UTestMockObject(type, fieldValues, methods)
            saveResolvedRef(ref.address, instance)

            val mockedMethodValues = mutableMapOf<JcMethod, MutableList<UIndexedMethodReturnValue<JcMethod, *>>>()
            mocks.filterIsInstance<UIndexedMethodReturnValue<JcMethod, *>>().forEach { mockValue ->
                // todo: filter out approximations-only methods
                mockedMethodValues.getOrPut(mockValue.method) { mutableListOf() }.add(mockValue)
            }

            mockedMethodValues.forEach { (method, values) ->
                val mockedValueType = requireNotNull(ctx.cp.findTypeOrNull(method.returnType)) {
                    "No such type found: ${method.returnType}"
                }

                methods[method] = values
                    .sortedBy { it.callIndex }
                    .map { resolveExpr(it, mockedValueType) }
            }

            val fields = generateSequence(type.jcClass) { it.superClass }
                .map { it.toType() }
                .flatMap { it.declaredFields }
                .filter { !it.isStatic }
                .filterNot { it.field is JcEnrichedVirtualField }

            for (field in fields) {
                val lvalue = UFieldLValue(ctx.typeToSort(field.fieldType), heapRef, field.field)
                val fieldValue = resolveLValue(lvalue, field.fieldType)

                fieldValues[field.field] = fieldValue
            }

            return instance
        }
    }
}
