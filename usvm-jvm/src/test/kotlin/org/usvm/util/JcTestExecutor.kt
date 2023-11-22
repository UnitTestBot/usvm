package org.usvm.util

import io.ksmt.utils.asExpr
import kotlinx.coroutines.runBlocking
import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.jacodb.impl.fs.BuildFolderLocation
import org.jacodb.impl.fs.JarLocation
import org.usvm.*
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.typeStreamOf
import org.usvm.api.util.JcTestResolver
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.machine.*
import org.usvm.machine.interpreter.JcFixedInheritorsNumberTypeSelector
import org.usvm.machine.interpreter.JcTypeStreamPrioritization
import org.usvm.machine.state.JcState
import org.usvm.machine.state.localIdx
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.types.first

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
        stringConstants: Map<String, UConcreteHeapRef>
    ): JcTest {
        val model = state.models.first()

        val ctx = state.ctx

        val memoryScope = MemoryScope(ctx, model, model, stringConstants, method)

        val staticsToResolve = ctx.primitiveTypes
            .flatMap {
                val sort = ctx.typeToSort(it)
                val regionId = JcStaticFieldRegionId(sort)
                val region = state.memory.getRegion(regionId) as JcStaticFieldsMemoryRegion<*>

                region.mutableStaticFields
            }

        val before: JcParametersState
        val after: JcParametersState
        val uTest = memoryScope.createUTest(staticsToResolve)

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

        return JcTest(
            method, before, after, result, coverage
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    private fun emptyJcParametersState() = JcParametersState(null, listOf(), emptyMap()) // TODO

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     */
    private class MemoryScope(
        ctx: JcContext,
        model: UModelBase<JcType>,
        memory: UReadOnlyMemory<JcType>,
        stringConstants: Map<String, UConcreteHeapRef>,
        method: JcTypedMethod,
    ) : JcTestStateResolver<UTestExpression>(ctx, model, memory, stringConstants, method) {

        override val decoderApi = JcTestExecutorDecoderApi(ctx)

        fun createUTest(staticsToResolve: List<JcField>): UTest {
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterStackLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                UTestNullExpression(ctx.cp.objectType)
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterStackLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            val initStmts = decoderApi.initializerInstructions()
            val statics = staticsToResolve.map { field ->
                        val fieldType = ctx.cp.findTypeOrNull(field.type.typeName)
                            ?: error("No such type ${field.type} found")
                        val sort = ctx.typeToSort(fieldType)

                        field to resolveLValue(JcStaticFieldLValue(field, sort), fieldType)
                }

            val initStmts = thisInstance.second +
                    parameters.flatMap { it.second } +
                    statics.map { UTestSetStaticFieldStatement(it.first, it.second.first) } +
                    statics.flatMap { it.second.second }

            val callExpr = if (method.isStatic) {
                UTestStaticMethodCall(method.method, parameters)
            } else {
                UTestMethodCall(thisInstance, method.method, parameters)
            }

            return UTest(initStmts, callExpr)
        }

        override fun allocateClassInstance(type: JcClassType): UTestExpression =
            UTestAllocateMemoryCall(type.jcClass)

        // todo: looks incorrect
        override fun allocateString(value: UTestExpression): UTestExpression = value
    }
}
