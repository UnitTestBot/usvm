package org.usvm.api.util

import io.ksmt.utils.asExpr
import kotlinx.coroutines.runBlocking
import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UArrayIndexLValue
import org.usvm.UArrayLengthLValue
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFieldLValue
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.URegisterLValue
import org.usvm.USort
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.machine.JcContext
import org.usvm.machine.extractBool
import org.usvm.machine.extractByte
import org.usvm.machine.extractChar
import org.usvm.machine.extractDouble
import org.usvm.machine.extractFloat
import org.usvm.machine.extractInt
import org.usvm.machine.extractLong
import org.usvm.machine.extractShort
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.WrappedException
import org.usvm.machine.state.localIdx
import org.usvm.memory.UReadOnlySymbolicMemory
import org.usvm.model.UModelBase
import org.usvm.types.takeFirst

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestResolver(
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    val pathToJars: List<String>,
    val classpath: JcClasspath,
) {

    private val runner: UTestConcreteExecutor
        get() {
            if (!UTestRunner.isInitialized()) {
                UTestRunner.initRunner(pathToJars, classpath)
            }
            return UTestRunner.runner
        }

    private val descriptor2ValueConverter = Descriptor2ValueConverter(classLoader)

    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()
        val memory = state.memory

        val ctx = state.pathConstraints.ctx as JcContext

        val initialScope = MemoryScope(ctx, model, model, method, classLoader, runner)
        //val afterScope = MemoryScope(ctx, model, memory, method, classLoader, runner)

        var before = JcParametersState(null, listOf())
        var after = JcParametersState(null, listOf())
        val uTest = initialScope.createUTest()

        val execResult = runBlocking {
            runner.executeAsync(uTest)
        }
        val result =
            when (execResult) {
                is UTestExecutionSuccessResult -> {
                    val thisBeforeDescr = execResult.initialState.instanceDescriptor
                    val thisBefore = thisBeforeDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val beforeArgsDescr = execResult.initialState.argsDescriptors
                    val argsBefore = beforeArgsDescr?.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    } ?: listOf()
                    before = JcParametersState(thisBefore, argsBefore)
                    val thisAfterDescr = execResult.resultState.instanceDescriptor
                    val thisAfter = thisAfterDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val afterArgsDescr = execResult.resultState.argsDescriptors
                    val argsAfter = afterArgsDescr?.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    } ?: listOf()
                    after = JcParametersState(thisAfter, argsAfter)
                    Result.success(execResult.result?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) })
                }

                is UTestExecutionExceptionResult -> {
                    val exceptionName = execResult.cause.split("\n").first()
                    val exceptionInstance = Class.forName(exceptionName.substringBefore(":"))
                        .constructors
                        .first { it.parameterCount == 0 }
                        .newInstance() as Throwable
                    val thisBeforeDescr = execResult.initialState.instanceDescriptor
                    val thisBefore = thisBeforeDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val beforeArgsDescr = execResult.initialState.argsDescriptors
                    val argsBefore = beforeArgsDescr?.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    } ?: listOf()
                    before = JcParametersState(thisBefore, argsBefore)
                    after = before
                    Result.failure(exceptionInstance)
                }

                else -> {
                    println("RES = $execResult")
                    error("No result")
                }
            }

        val coverage = resolveCoverage(method, state)

        return JcTest(
            method, before, after, result, coverage
        )
    }

    private fun resolveException(exception: Exception): Throwable = when (exception) {
        is WrappedException -> Reflection.allocateInstance(classLoader.loadClass(exception.name)) as Throwable
        else -> exception
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     * @param classLoader a class loader to load target classes.
     */
    private class MemoryScope(
        private val ctx: JcContext,
        private val model: UModelBase<JcField, JcType>,
        private val memory: UReadOnlySymbolicMemory<JcType>,
        private val method: JcTypedMethod,
        private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
        private val runner: UTestConcreteExecutor
    ) {

        private val resolvedCache = mutableMapOf<UConcreteHeapAddress, Pair<UTestExpression, List<UTestExpression>>>()

        fun createUTest(): UTest {
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                UTestNullExpression(ctx.cp.objectType) to listOf()
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            val initStmts = thisInstance.second + parameters.flatMap { it.second }
            val callExpr = if (method.isStatic) {
                UTestStaticMethodCall(method.method, parameters.map { it.first })
            } else {
                UTestMethodCall(thisInstance.first, method.method, parameters.map { it.first })
            }
            return UTest(initStmts, callExpr)
        }

        fun resolveState(): JcParametersState {
            // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                null
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            return JcParametersState(thisInstance, parameters)
        }

        fun resolveLValue(lvalue: ULValue, type: JcType): Pair<UTestExpression, List<UTestExpression>> {
            val expr = memory.read(lvalue)

            return resolveExpr(expr, type)
        }

        fun resolveExpr(expr: UExpr<out USort>, type: JcType): Pair<UTestExpression, List<UTestExpression>> =
            when (type) {
                is JcPrimitiveType -> resolvePrimitive(expr, type)
                is JcRefType -> resolveReference(expr.asExpr(ctx.addressSort), type)
                else -> error("Unexpected type: $type")
            }

        fun resolvePrimitive(
            expr: UExpr<out USort>, type: JcPrimitiveType
        ): Pair<UTestExpression, List<UTestExpression>> {
            val exprInModel = evaluateInModel(expr)
            return when (type) {
                ctx.cp.boolean -> UTestBooleanExpression(extractBool(exprInModel) ?: false, ctx.cp.boolean)
                ctx.cp.short -> UTestShortExpression(extractShort(exprInModel) ?: 0, ctx.cp.short)
                ctx.cp.int -> UTestIntExpression(extractInt(exprInModel) ?: 0, ctx.cp.int)
                ctx.cp.long -> UTestLongExpression(extractLong(exprInModel) ?: 0L, ctx.cp.long)
                ctx.cp.float -> UTestFloatExpression(extractFloat(exprInModel) ?: 0.0f, ctx.cp.float)
                ctx.cp.double -> UTestDoubleExpression(extractDouble(exprInModel) ?: 0.0, ctx.cp.double)
                ctx.cp.byte -> UTestByteExpression(extractByte(exprInModel) ?: 0, ctx.cp.byte)
                ctx.cp.char -> UTestCharExpression(extractChar(exprInModel) ?: '\u0000', ctx.cp.char)
                ctx.cp.void -> UTestNullExpression(ctx.cp.void)
                else -> error("Unexpected type: ${type.typeName}")
            }.let { it to listOf() }
        }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Pair<UTestExpression, List<UTestExpression>> {
            val ref = evaluateInModel(heapRef) as UConcreteHeapRef
            if (ref.address == NULL_ADDRESS) {
                return UTestNullExpression(type) to listOf()
            }
            return resolvedCache.getOrElse(ref.address) {
                // to find a type, we need to understand the source of the object
                val evaluatedType = if (ref.address <= INITIAL_INPUT_ADDRESS) {
                    // input object
                    val typeStream = model.typeStreamOf(ref).filterBySupertype(type)
                    typeStream.takeFirst() as JcRefType
                } else {
                    // allocated object
                    memory.typeStreamOf(ref).takeFirst()
                }
                when (evaluatedType) {
                    is JcArrayType -> resolveArray(ref, heapRef, evaluatedType)
                    is JcClassType -> resolveObject(ref, heapRef, evaluatedType)
                    else -> error("Unexpected type: $type")
                }
            }
        }

        private fun resolveArray(
            ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcArrayType
        ): Pair<UTestExpression, List<UTestExpression>> {
            val lengthRef = UArrayLengthLValue(heapRef, ctx.arrayDescriptorOf(type))
            val arrLength = resolveLValue(lengthRef, ctx.cp.int).first as UTestIntExpression
            val length = if (arrLength.value in 0..10_000) arrLength else UTestIntExpression(0, ctx.cp.int) // TODO hack

            val cellSort = ctx.typeToSort(type.elementType)

            fun resolveElement(idx: Int): Pair<UTestExpression, List<UTestExpression>> {
                val elemRef = UArrayIndexLValue(cellSort, heapRef, ctx.mkBv(idx), ctx.arrayDescriptorOf(type))
                return resolveLValue(elemRef, type.elementType)
            }

            //val arrLength = UTestIntExpression(length, ctx.cp.int)
            val arrayInstance = UTestCreateArrayExpression(type.elementType, length)

            val arraySetters = buildList {
                for (i in 0 until length.value) {
                    with(resolveElement(i)) {
                        add(UTestArraySetStatement(arrayInstance, UTestIntExpression(i, ctx.cp.int), first))
                        addAll(second)
                    }
                }
            }

            resolvedCache[ref.address] = arrayInstance to arraySetters
            return arrayInstance to arraySetters
        }

        private fun resolveObject(
            ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcRefType
        ): Pair<UTestExpression, List<UTestExpression>> {
            val exprs = mutableListOf<UTestExpression>()
            val instance = UTestAllocateMemoryCall(type.jcClass)//Reflection.allocateInstance(clazz)

            val fieldSetters = mutableListOf<UTestExpression>()
            resolvedCache[ref.address] = instance to fieldSetters

            exprs.add(instance)

            val fields =
                generateSequence(type.jcClass) { it.superClass }.map { it.toType() }.flatMap { it.declaredFields }
                    .filter { !it.isStatic }

            for (field in fields) {
                val lvalue = UFieldLValue(ctx.typeToSort(field.fieldType), heapRef, field.field)
                val fieldValue = resolveLValue(lvalue, field.fieldType)
                val uTestSetFieldStmt = UTestSetFieldStatement(instance, field.field, fieldValue.first)
                fieldSetters.addAll(fieldValue.second)
                fieldSetters.add(uTestSetFieldStmt)
            }
            return instance to fieldSetters
        }

        private fun resolveType(type: JcRefType): Class<*> = type.ifArrayGetElementType?.let { elementType ->
            when (elementType) {
                ctx.cp.boolean -> BooleanArray::class.java
                ctx.cp.short -> ShortArray::class.java
                ctx.cp.int -> IntArray::class.java
                ctx.cp.long -> LongArray::class.java
                ctx.cp.float -> FloatArray::class.java
                ctx.cp.double -> DoubleArray::class.java
                ctx.cp.byte -> ByteArray::class.java
                ctx.cp.char -> CharArray::class.java
                is JcRefType -> {
                    val clazz = resolveType(elementType)
                    Reflection.allocateArray(clazz, length = 0).javaClass
                }

                else -> error("Unexpected type: $elementType")
            }
        } ?: classLoader.loadClass(type.jcClass.name)

        /**
         * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
         *
         * @return a concretized expression.
         */
        private fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
            return model.eval(expr)
        }
    }

}