package org.usvm.util

import io.ksmt.utils.asExpr
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.LocationType
import org.jacodb.api.ext.allSuperHierarchySequence
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.enumValues
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.jacodb.impl.fs.BuildFolderLocation
import org.jacodb.impl.fs.JarLocation
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicList
import org.usvm.api.SymbolicMap
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.ObjectDecoder
import org.usvm.api.internal.SymbolicListImpl
import org.usvm.api.typeStreamOf
import org.usvm.api.util.JcTestDecoders
import org.usvm.api.util.JcTestResolver
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestArrayGetExpression
import org.usvm.instrumentation.testcase.api.UTestArrayLengthExpression
import org.usvm.instrumentation.testcase.api.UTestArraySetStatement
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression
import org.usvm.instrumentation.testcase.api.UTestByteExpression
import org.usvm.instrumentation.testcase.api.UTestCastExpression
import org.usvm.instrumentation.testcase.api.UTestCharExpression
import org.usvm.instrumentation.testcase.api.UTestClassExpression
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestCreateArrayExpression
import org.usvm.instrumentation.testcase.api.UTestDoubleExpression
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionFailedResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestFloatExpression
import org.usvm.instrumentation.testcase.api.UTestGetFieldExpression
import org.usvm.instrumentation.testcase.api.UTestGetStaticFieldExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression
import org.usvm.instrumentation.testcase.api.UTestLongExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement
import org.usvm.instrumentation.testcase.api.UTestSetStaticFieldStatement
import org.usvm.instrumentation.testcase.api.UTestShortExpression
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.testcase.api.UTestStringExpression
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.util.stringType
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
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
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
    override fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()

        val ctx = state.ctx

        val memoryScope = MemoryScope(ctx, model, model, method)

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
                    before = JcParametersState(thisBefore, argsBefore)
                    val thisAfterDescr = execResult.resultState.instanceDescriptor
                    val thisAfter = thisAfterDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val afterArgsDescr = execResult.resultState.argsDescriptors.map { it }
                    val argsAfter = afterArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }
                    after = JcParametersState(thisAfter, argsAfter)
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
                    before = JcParametersState(thisBefore, argsBefore)
                    val thisAfterDescr = execResult.resultState.instanceDescriptor
                    val thisAfter = thisAfterDescr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                    val afterArgsDescr = execResult.resultState.argsDescriptors.map { it }
                    val argsAfter = afterArgsDescr.let { descriptors ->
                        descriptors.map { descr ->
                            descr?.let { descriptor2ValueConverter.buildObjectFromDescriptor(it) }
                        }
                    }
                    after = JcParametersState(thisAfter, argsAfter)
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

    private fun emptyJcParametersState() = JcParametersState(null, listOf())

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     */
    private class MemoryScope(
        private val ctx: JcContext,
        private val model: UModelBase<JcType>,
        private val memory: UReadOnlyMemory<JcType>,
        private val method: JcTypedMethod,
    ) {

        private val decoders = JcTestDecoders(ctx.cp)
        private val typeSelector = JcTypeStreamPrioritization(
            typesToScore = JcFixedInheritorsNumberTypeSelector.DEFAULT_INHERITORS_NUMBER_TO_SCORE
        )
        private val resolvedCache = mutableMapOf<UConcreteHeapAddress, Pair<UTestExpression, List<UTestInst>>>()

        fun createUTest(): UTest {
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterStackLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                UTestNullExpression(ctx.cp.objectType) to listOf()
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterStackLValue(ctx.typeToSort(param.type), registerIdx)
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


        fun resolveLValue(lvalue: ULValue<*, *>, type: JcType): Pair<UTestExpression, List<UTestInst>> =
            resolveExpr(memory.read(lvalue), type)


        fun resolveExpr(expr: UExpr<out USort>, type: JcType): Pair<UTestExpression, List<UTestInst>> =
            when (type) {
                is JcPrimitiveType -> resolvePrimitive(expr, type)
                is JcRefType -> resolveReference(expr.asExpr(ctx.addressSort), type)
                else -> error("Unexpected type: $type")
            }

        fun resolvePrimitive(
            expr: UExpr<out USort>, type: JcPrimitiveType
        ): Pair<UTestExpression, List<UTestInst>> {
            val exprInModel = evaluateInModel(expr)
            return when (type) {
                ctx.cp.boolean -> UTestBooleanExpression(
                    value = extractBool(exprInModel) ?: false,
                    type = ctx.cp.boolean
                )
                ctx.cp.short -> UTestShortExpression(
                    value = extractShort(exprInModel) ?: 0,
                    type = ctx.cp.short
                )
                ctx.cp.int -> UTestIntExpression(
                    value = extractInt(exprInModel) ?: 0,
                    type = ctx.cp.int
                )
                ctx.cp.long -> UTestLongExpression(
                    value = extractLong(exprInModel) ?: 0L,
                    type = ctx.cp.long
                )
                ctx.cp.float -> UTestFloatExpression(
                    value = extractFloat(exprInModel) ?: 0.0f,
                    type = ctx.cp.float
                )
                ctx.cp.double -> UTestDoubleExpression(
                    value = extractDouble(exprInModel) ?: 0.0,
                    type = ctx.cp.double
                )
                ctx.cp.byte -> UTestByteExpression(
                    value = extractByte(exprInModel) ?: 0,
                    type = ctx.cp.byte
                )
                ctx.cp.char -> UTestCharExpression(
                    value = extractChar(exprInModel) ?: '\u0000',
                    type = ctx.cp.char
                )
                ctx.cp.void -> UTestNullExpression(
                    type = ctx.cp.void
                )
                else -> error("Unexpected type: ${type.typeName}")
            }.let { it to listOf() }
        }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Pair<UTestExpression, List<UTestInst>> {
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

            val evaluatedType = typeSelector.firstOrNull(typeStream, type.jcClass)
                ?: return UTestNullExpression(type) to listOf()

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
        ): Pair<UTestExpression, List<UTestInst>> {
            val arrayDescriptor = ctx.arrayDescriptorOf(type)
            val lengthRef = UArrayLengthLValue(heapRef, arrayDescriptor, ctx.sizeSort)
            val resolvedLength = resolveLValue(lengthRef, ctx.cp.int).first as UTestIntExpression

            val length = clipArrayLength(resolvedLength)

            val cellSort = ctx.typeToSort(type.elementType)

            fun resolveElement(idx: Int): Pair<UTestExpression, List<UTestInst>> {
                val elemRef = UArrayIndexLValue(cellSort, heapRef, ctx.mkBv(idx), arrayDescriptor)
                return resolveLValue(elemRef, type.elementType)
            }

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
            ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType
        ): Pair<UTestExpression, List<UTestInst>> {
            val decoder = decoders.findDecoder(type.jcClass)
            if (decoder != null) {
                val instanceWithInitializer = decodeObject(ref, type, decoder)
                resolvedCache[ref.address] = instanceWithInitializer
                return instanceWithInitializer
            }

            if (type.jcClass == ctx.classType.jcClass && ref.address <= INITIAL_STATIC_ADDRESS) {
                // Note that non-negative addresses are possible only for the result value.
                return resolveAllocatedClass(ref)
            }

            if (type.jcClass == ctx.stringType.jcClass && ref.address <= INITIAL_STATIC_ADDRESS) {
                // Note that non-negative addresses are possible only for the result value.
                return resolveAllocatedString(ref)
            }

            val anyEnumAncestor = type.getEnumAncestorOrNull()
            if (anyEnumAncestor != null) {
                return resolveEnumValue(heapRef, anyEnumAncestor)
            }


            val exprs = mutableListOf<UTestExpression>()
            val instance = UTestAllocateMemoryCall(type.jcClass)

            val fieldSetters = mutableListOf<UTestInst>()
            resolvedCache[ref.address] = instance to fieldSetters

            exprs.add(instance)

            val fields =
                generateSequence(type.jcClass) { it.superClass }
                    .map { it.toType() }
                    .flatMap { it.declaredFields }
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

        private fun resolveEnumValue(
            heapRef: UHeapRef,
            enumAncestor: JcClassOrInterface
        ): Pair<UTestExpression, List<UTestInst>> {
            with(ctx) {
                val ordinalLValue = UFieldLValue(sizeSort, heapRef, enumOrdinalField)
                val ordinalFieldValue = resolveLValue(ordinalLValue, cp.int).first as UTestIntExpression
                val enumField = enumAncestor.enumValues?.get(ordinalFieldValue.value)
                    ?: error("Cant find enum field with index ${ordinalFieldValue.value}")

                return UTestGetStaticFieldExpression(enumField) to listOf()
            }
        }

        private fun resolveAllocatedClass(ref: UConcreteHeapRef): Pair<UTestExpression, List<UTestInst>> {
            val classTypeField = ctx.classTypeSyntheticField
            val classTypeLValue = UFieldLValue(ctx.addressSort, ref, classTypeField)
            val classTypeRef = memory.read(classTypeLValue) as? UConcreteHeapRef
                ?: error("No type for allocated class")

            val classType = memory.typeStreamOf(classTypeRef).first()
            return UTestClassExpression(classType) to listOf()
        }

        private fun resolveAllocatedString(ref: UConcreteHeapRef): Pair<UTestExpression, List<UTestInst>> {
            val valueField = ctx.stringValueField
            val strValueLValue = UFieldLValue(ctx.typeToSort(valueField.fieldType), ref, valueField.field)
            return resolveLValue(strValueLValue, valueField.fieldType)
        }

        private fun decodeObject(
            ref: UConcreteHeapRef,
            type: JcClassType,
            objectDecoder: ObjectDecoder
        ): Pair<UTestExpression, List<UTestInst>> {
            val refDecoder = TestDecoder(ref)
            val decodedObject = objectDecoder.decode(refDecoder, type.jcClass)
            return decodedObject to refDecoder.instructions
        }

        private fun resolveSymbolicList(heapRef: UHeapRef): Pair<SymbolicList<UTestExpression>, List<UTestInst>>? {
            val ref = evaluateInModel(heapRef) as UConcreteHeapRef
            if (ref.address == NULL_ADDRESS) {
                return null
            }

            val listType = ctx.cp.findTypeOrNull<SymbolicList<*>>() ?: return null
            val instructions = mutableListOf<UTestInst>()

            val lengthRef = UArrayLengthLValue(heapRef, listType, ctx.sizeSort)
            val (resolvedLength, lengthInst) = resolveLValue(lengthRef, ctx.cp.int)
            instructions += lengthInst

            val length = clipArrayLength(resolvedLength as UTestIntExpression)

            val result = SymbolicListImpl<UTestExpression>()
            for (i in 0 until length.value) {
                val elemRef = UArrayIndexLValue(ctx.addressSort, heapRef, ctx.mkSizeExpr(i), listType)
                val (element, elementInst) = resolveLValue(elemRef, ctx.cp.objectType)
                instructions += elementInst
                result.insert(i, element)
            }

            return result to instructions
        }

        private inner class TestDecoder(
            private val instanceRef: UConcreteHeapRef
        ) : DecoderApi<UTestExpression> {
            val instructions = mutableListOf<UTestInst>()

            override fun decodeField(field: JcField): UTestExpression {
                val lvalue = UFieldLValue(ctx.typeToSort(field.typed().fieldType), instanceRef, field)
                val (res, inst) = resolveLValue(lvalue, field.typed().fieldType)
                instructions += inst
                return res
            }

            override fun decodeSymbolicListField(field: JcField): SymbolicList<UTestExpression>? {
                val lvalue = UFieldLValue(ctx.addressSort, instanceRef, field)
                val listRef = memory.read(lvalue)
                val (res, inst) = resolveSymbolicList(listRef) ?: return null
                instructions += inst
                return res
            }

            override fun decodeSymbolicMapField(field: JcField): SymbolicMap<UTestExpression, UTestExpression>? {
                TODO("Not yet implemented")
            }

            override fun decodeSymbolicIdentityMapField(
                field: JcField
            ): SymbolicIdentityMap<UTestExpression, UTestExpression>? {
                TODO("Not yet implemented")
            }

            override fun setField(field: JcField, instance: UTestExpression, value: UTestExpression) {
                instructions += if (field.isStatic) {
                    UTestSetStaticFieldStatement(field, value)
                } else {
                    UTestSetFieldStatement(instance, field, value)
                }
            }

            override fun getField(field: JcField, instance: UTestExpression): UTestExpression =
                if (field.isStatic) {
                    UTestGetStaticFieldExpression(field)
                } else {
                    UTestGetFieldExpression(instance, field)
                }

            override fun invokeMethod(method: JcMethod, args: List<UTestExpression>): UTestExpression =
                when {
                    method.isConstructor -> UTestConstructorCall(method, args)
                    method.isStatic -> UTestStaticMethodCall(method, args)
                    else -> UTestMethodCall(args.first(), method, args.drop(1))
                }

            override fun createBoolConst(value: Boolean): UTestExpression =
                UTestBooleanExpression(value, ctx.cp.boolean)

            override fun createByteConst(value: Byte): UTestExpression =
                UTestByteExpression(value, ctx.cp.byte)

            override fun createShortConst(value: Short): UTestExpression =
                UTestShortExpression(value, ctx.cp.short)

            override fun createIntConst(value: Int): UTestExpression =
                UTestIntExpression(value, ctx.cp.int)

            override fun createLongConst(value: Long): UTestExpression =
                UTestLongExpression(value, ctx.cp.long)

            override fun createFloatConst(value: Float): UTestExpression =
                UTestFloatExpression(value, ctx.cp.float)

            override fun createDoubleConst(value: Double): UTestExpression =
                UTestDoubleExpression(value, ctx.cp.double)

            override fun createCharConst(value: Char): UTestExpression =
                UTestCharExpression(value, ctx.cp.char)

            override fun createStringConst(value: String): UTestExpression =
                UTestStringExpression(value, ctx.cp.stringType())

            override fun createClassConst(cls: JcClassOrInterface): UTestExpression =
                UTestClassExpression(cls.toType())

            override fun createNullConst(): UTestExpression =
                UTestNullExpression(ctx.cp.objectType)

            override fun setArrayIndex(array: UTestExpression, index: UTestExpression, value: UTestExpression) {
                instructions += UTestArraySetStatement(array, index, value)
            }

            override fun getArrayIndex(array: UTestExpression, index: UTestExpression): UTestExpression =
                UTestArrayGetExpression(array, index)

            override fun getArrayLength(array: UTestExpression): UTestExpression =
                UTestArrayLengthExpression(array)

            override fun createArray(elementType: JcType, size: UTestExpression): UTestExpression =
                UTestCreateArrayExpression(elementType, size)

            override fun castClass(type: JcClassOrInterface, obj: UTestExpression): UTestExpression =
                UTestCastExpression(obj, type.toType())
        }

        /**
         * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
         *
         * @return a concretized expression.
         */
        private fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
            return model.eval(expr)
        }

        // TODO simple org.jacodb.api.ext.JcClasses.isEnum does not work with enums with abstract methods
        private fun JcRefType.getEnumAncestorOrNull(): JcClassOrInterface? =
            jcClass.getAllSuperHierarchyIncludingThis().firstOrNull { it.isEnum }

        private fun JcClassOrInterface.getAllSuperHierarchyIncludingThis() =
            (sequenceOf(this) + allSuperHierarchySequence)

        private fun JcField.typed(): JcTypedField =
            enclosingClass.toType()
                .findFieldOrNull(name)
                ?: error("Field not found: $this")
    }

    companion object {
        fun clipArrayLength(length: UTestIntExpression): UTestIntExpression =
            when {
                length.value in 0..MAX_ARRAY_LENGTH -> length

                length.value > MAX_ARRAY_LENGTH -> {
                    org.usvm.logger.warn { "Array length exceeds $MAX_ARRAY_LENGTH: $length" }
                    UTestIntExpression(MAX_ARRAY_LENGTH, length.type)
                }

                else -> {
                    org.usvm.logger.warn { "Negative array length: $length" }
                    UTestIntExpression(0, length.type)
                }
            }

        private const val MAX_ARRAY_LENGTH = 10_000
    }

}