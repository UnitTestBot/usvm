package org.usvm.util

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
import org.usvm.api.util.JcTestResolver
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
import org.usvm.machine.state.JcState
import org.usvm.machine.state.localIdx
import org.usvm.memory.UReadOnlySymbolicMemory
import org.usvm.model.UModelBase
import org.usvm.types.firstOrNull
import java.util.concurrent.CancellationException

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestExecutor(
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    val pathToJars: List<String>,
    val classpath: JcClasspath,
) : JcTestResolver {

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
    override fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()

        val ctx = state.pathConstraints.ctx as JcContext

        val memoryScope = MemoryScope(ctx, model, model, method)

        val before: JcParametersState
        val after: JcParametersState
        val uTest = memoryScope.createUTest()

        val execResult = try {
            runBlocking {
                runner.executeAsync(uTest)
            }
        } catch (e: CancellationException) {
            return JcTest(
                method = method,
                before = JcParametersState(null, listOf()),
                after = JcParametersState(null, listOf()),
                result = Result.failure(IllegalAccessException()),
                coverage = JcCoverage(emptyMap())
            )
        }
        val result = when (execResult) {
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
                val exceptionInstance =
                    Class.forName(exceptionName.substringBefore(":")).constructors.first { it.parameterCount == 0 }
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

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     */
    private class MemoryScope(
        private val ctx: JcContext,
        private val model: UModelBase<JcField, JcType>,
        private val memory: UReadOnlySymbolicMemory<JcType>,
        private val method: JcTypedMethod
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


        fun resolveLValue(lvalue: ULValue, type: JcType): Pair<UTestExpression, List<UTestExpression>> =
            resolveExpr(memory.read(lvalue), type)


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
            // to find a type, we need to understand the source of the object
            val typeStream = if (ref.address <= INITIAL_INPUT_ADDRESS) {
                // input object
                model.typeStreamOf(ref)
            } else {
                // allocated object
                memory.typeStreamOf(ref)
            }.filterBySupertype(type)

            // We filter allocated object type stream, because it could be stored in the input array,
            // which resolved to a wrong type, since we do not build connections between element types
            // and array types right now.
            // In such cases, we need to resolve this element to null.

            val evaluatedType = typeStream.firstOrNull() ?: return UTestNullExpression(type) to listOf()

            // We check for the type stream emptiness firsly and only then for the resolved cache,
            // because even if the object is already resolved, it could be incompatible with the [type], if it
            // is an element of an array of the wrong type.

            return resolvedCache.getOrElse(ref.address) {
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
            val instance = UTestAllocateMemoryCall(type.jcClass)

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