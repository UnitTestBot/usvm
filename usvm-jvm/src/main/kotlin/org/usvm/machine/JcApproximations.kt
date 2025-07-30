package org.usvm.machine

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.BsmArg
import org.jacodb.api.jvm.cfg.BsmDoubleArg
import org.jacodb.api.jvm.cfg.BsmFloatArg
import org.jacodb.api.jvm.cfg.BsmHandle
import org.jacodb.api.jvm.cfg.BsmIntArg
import org.jacodb.api.jvm.cfg.BsmLongArg
import org.jacodb.api.jvm.cfg.BsmMethodTypeArg
import org.jacodb.api.jvm.cfg.BsmStringArg
import org.jacodb.api.jvm.cfg.BsmTypeArg
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.findClassOrNull
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.ifArrayGetElementType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectClass
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.short
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.void
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.Engine
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicList
import org.usvm.api.SymbolicMap
import org.usvm.api.collection.ListCollectionApi.ensureListSizeCorrect
import org.usvm.api.collection.ListCollectionApi.mkSymbolicList
import org.usvm.api.collection.ListCollectionApi.symbolicListCopyRange
import org.usvm.api.collection.ListCollectionApi.symbolicListGet
import org.usvm.api.collection.ListCollectionApi.symbolicListInsert
import org.usvm.api.collection.ListCollectionApi.symbolicListRemove
import org.usvm.api.collection.ListCollectionApi.symbolicListSet
import org.usvm.api.collection.ListCollectionApi.symbolicListSize
import org.usvm.api.collection.ObjectMapCollectionApi.ensureObjectMapSizeCorrect
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapMergeInto
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArray
import org.usvm.api.initializeArrayLength
import org.usvm.api.internal.SymbolicListImpl
import org.usvm.api.internal.SymbolicMapImpl
import org.usvm.api.makeNullableSymbolicRefSubtype
import org.usvm.api.makeNullableSymbolicRefWithSameType
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRef
import org.usvm.api.makeSymbolicRefSubtype
import org.usvm.api.makeSymbolicRefWithSameType
import org.usvm.api.mapTypeStreamNotNull
import org.usvm.api.memcpy
import org.usvm.api.objectTypeEquals
import org.usvm.api.objectTypeSubtype
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.getIntValue
import org.usvm.jvm.util.allInstanceFields
import org.usvm.jvm.util.javaName
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.mocks.mockMethod
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationAndBoxIfNeeded
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.memory.foldHeapRefWithStaticAsConcrete
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.types.first
import org.usvm.types.singleOrNull
import org.usvm.util.allocHeapRef
import org.usvm.util.write
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction0
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2

class JcMethodApproximationResolver(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
) {
    private var currentScope: JcStepScope? = null
    private val scope: JcStepScope
        get() = checkNotNull(currentScope)

    private var currentExprResolver: JcExprResolver? = null
    private val exprResolver: JcExprResolver
        get() = checkNotNull(currentExprResolver)

    private val usvmApiEngine by lazy { ctx.cp.findClassOrNull<Engine>() }
    private val usvmApiSymbolicList by lazy { ctx.cp.findClassOrNull<SymbolicList<*>>() }
    private val usvmApiSymbolicListImpl by lazy { ctx.cp.findClassOrNull<SymbolicListImpl<*>>() }
    private val usvmApiSymbolicMap by lazy { ctx.cp.findClassOrNull<SymbolicMap<*, *>>() }
    private val usvmApiSymbolicMapImpl by lazy { ctx.cp.findClassOrNull<SymbolicMapImpl<*, *>>() }
    private val usvmApiSymbolicIdentityMap by lazy { ctx.cp.findClassOrNull<SymbolicIdentityMap<*, *>>() }

    fun approximate(scope: JcStepScope, exprResolver: JcExprResolver, callJcInst: JcMethodCall): Boolean = try {
        this.currentScope = scope
        this.currentExprResolver = exprResolver
        approximate(callJcInst)
    } finally {
        this.currentScope = null
        this.currentExprResolver = null
    }

    private fun approximate(callJcInst: JcMethodCall): Boolean {
        if (skipMethodIfThrowable(callJcInst)) {
            return true
        }

        if (callJcInst is JcDynamicMethodCallInst) {
            return approximateInvokeDynamic(callJcInst)
        }

        if (callJcInst.method.isStatic) {
            return approximateStaticMethod(callJcInst)
        }

        return approximateRegularMethod(callJcInst)
    }

    private fun approximateRegularMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (enclosingClass == usvmApiSymbolicList) {
            approximateUsvmSymbolicListMethod(methodCall)
            return true
        }

        if (enclosingClass == usvmApiSymbolicMap) {
            approximateUsvmSymbolicMapMethod(methodCall)
            return true
        }

        if (enclosingClass == usvmApiSymbolicIdentityMap) {
            approximateUsvmSymbolicIdMapMethod(methodCall)
            return true
        }

        if (enclosingClass == ctx.cp.objectClass) {
            if (approximateObjectVirtualMethod(methodCall)) return true
        }

        if (enclosingClass == ctx.classType.jcClass) {
            if (approximateClassVirtualMethod(methodCall)) return true
        }

        if (className == "jdk.internal.misc.Unsafe") {
            if (approximateUnsafeVirtualMethod(methodCall)) return true
        }

        if (method.name == "clone" && enclosingClass == ctx.cp.objectClass) {
            if (approximateObjectClone(methodCall)) return true
        }

        return approximateEmptyNativeMethod(methodCall)
    }

    private fun approximateStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (enclosingClass == usvmApiEngine) {
            approximateUsvmApiEngineStaticMethod(methodCall)
            return true
        }

        if (enclosingClass == ctx.classType.jcClass) {
            if (approximateClassStaticMethod(methodCall)) return true
        }

        if (className == "java.lang.System") {
            if (approximateSystemStaticMethod(methodCall)) return true
        }

        if (className == "java.lang.StringUTF16") {
            if (approximateStringUtf16StaticMethod(methodCall)) return true
        }

        if (className == "java.lang.Float") {
            if (approximateFloatStaticMethod(methodCall)) return true
        }

        if (className == "java.lang.Double") {
            if (approximateDoubleStaticMethod(methodCall)) return true
        }

        if (className == "java.util.Calendar") {
            if (approximateCalendarStaticMethod(methodCall)) return true
        }

        if (className == "java.nio.charset.Charset") {
            if (approximateCharsetStaticMethod(methodCall)) return true
        }

        if (className == "jdk.internal.reflect.Reflection") {
            if (approximateJavaReflectionMethod(methodCall)) return true
        }

        if (className == "java.lang.reflect.Array") {
            if (approximateArrayReflectionMethod(methodCall)) return true
        }

        return approximateEmptyNativeMethod(methodCall)
    }

    private fun approximateEmptyNativeMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.isNative && method.hasVoidReturnType() && method.parameters.isEmpty()) {
            if (method.enclosingClass.declaration.location.isRuntime) {
                /**
                 * Native methods in the standard library with no return value and no
                 * arguments have no visible effect and can be skipped
                 * */
                scope.doWithState {
                    skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                }
                return true
            }
        }

        return false
    }

    private fun approximateInvokeDynamic(methodCallInst: JcDynamicMethodCallInst): Boolean = with(methodCallInst) {
        if (dynamicCall.method.method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
            if (approximateStringConcat(methodCallInst)) return true
        }

        return false
    }

    private fun approximateClassStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        /**
         * Approximate retrieval of class instance for primitives.
         * */
        if (method.name == "getPrimitiveClass") {
            val classNameRef = arguments.single()

            val primitiveTypes = ctx.primitiveTypes + ctx.cp.void
            val predefinedTypeNames = primitiveTypes.associateBy {
                exprResolver.simpleValueResolver.resolveStringConstant(it.typeName)
            }

            val primitive = predefinedTypeNames[classNameRef] ?: return false

            val classRef = exprResolver.simpleValueResolver.resolveClassRef(primitive)

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, classRef)
            }

            return true
        }

        return false
    }

    private fun approximateClassVirtualMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        /**
         * Approximate assertions enabled check.
         * It is correct to enable assertions during analysis.
         * */
        if (method.name == "desiredAssertionStatus") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.trueExpr)
            }
            return true
        }

        if (method.name == "isInstance") {
            val classRef = arguments[0].asExpr(ctx.addressSort)
            val objectRef = arguments[1].asExpr(ctx.addressSort)
            scope.doWithState {
                val classRefTypeRepresentative =
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                classRefTypeRepresentative as UConcreteHeapRef
                val classType = memory.types.typeOf(classRefTypeRepresentative.address)
                val isExpr = memory.types.evalIsSubtype(objectRef, classType)
                skipMethodInvocationWithValue(methodCall, isExpr)
            }

            return true
        }

        return false
    }

    private fun approximateObjectVirtualMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "getClass") {
            val instance = arguments.first().asExpr(ctx.addressSort)

            val result = scope.calcOnState {
                mapTypeStreamNotNull(instance) { _, types ->
                    val type = types.singleOrNull()
                    type?.let { exprResolver.simpleValueResolver.resolveClassRef(it) }
                }
            } ?: return false

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateCalendarStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "getInstance") {
            val normalCalendar = ctx.cp.findTypeOrNull("java.util.GregorianCalendar")
                ?: return false

            scope.doWithState {
                val ref = memory.allocConcrete(normalCalendar)
                skipMethodInvocationWithValue(methodCall, ref)
            }

            return true
        }

        return false
    }

    private fun approximateCharsetStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "forName" || method.name == "defaultCharset") {
            val utf8 = ctx.cp.findTypeOrNull("sun.nio.cs.UTF_8") as? JcClassType
                ?: return false

            val utf8Instance = utf8.declaredFields.single { it.isStatic && it.name == "INSTANCE" }
            val fieldRef = JcFieldRef(instance = null, field = utf8Instance)
            val instanceValue = fieldRef.accept(exprResolver) ?: return true

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, instanceValue)
            }

            return true
        }

        return false
    }

    private fun approximateJavaReflectionMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "getCallerClass") {
            scope.doWithState {
                val callerClass = callStack.penultimateMethod().enclosingClass.toType()
                val classRef = exprResolver.simpleValueResolver.resolveClassRef(callerClass)
                skipMethodInvocationWithValue(methodCall, classRef)
            }

            return true
        }

        return false
    }

    private fun approximateArrayReflectionMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "newInstance" && method.parameters[1].type.isPrimitive) {
            scope.doWithState {
                val componentTypeRef = arguments[0].asExpr(ctx.addressSort)
                val componentTypeRepresentative =
                    memory.read(UFieldLValue(ctx.addressSort, componentTypeRef, ctx.classTypeSyntheticField))
                componentTypeRepresentative as UConcreteHeapRef
                val componentType = memory.types.typeOf(componentTypeRepresentative.address)
                val arrayType = ctx.cp.arrayTypeOf(componentType)
                val arrayRef = memory.allocConcrete(arrayType)
                val descriptor = ctx.arrayDescriptorOf(arrayType)
                val sizeExpr = arguments[1].asExpr(ctx.sizeSort)
                memory.initializeArrayLength(arrayRef, descriptor, ctx.sizeSort, sizeExpr)
                skipMethodInvocationWithValue(methodCall, arrayRef)
            }

            return true
        }

        if (method.name == "getLength") {
            val arrayRef = arguments[0].asExpr(ctx.addressSort)
            exprResolver.resolveGetArrayLength(methodCall, arrayRef)
            return true
        }

        if (method.name == "get") {
            val arrayRef = arguments[0].asExpr(ctx.addressSort)
            val index = arguments[1].asExpr(ctx.sizeSort)
            exprResolver.resolveGetArrayElem(methodCall, arrayRef, index)
            return true
        }

        if (method.name == "set") {
            val arrayRef = arguments[0].asExpr(ctx.addressSort)
            val index = arguments[1].asExpr(ctx.sizeSort)
            val value = arguments[2].asExpr(ctx.addressSort)
            exprResolver.resolveSetArrayElem(methodCall, arrayRef, index, value)
            return true
        }

        return false
    }

    // TODO: move to java-stdlib-appoximations
    private fun JcExprResolver.resolveGetArrayLength(
        methodCall: JcMethodCall,
        arrayRef: UHeapRef,
    ) = scope.doWithState {
        checkNullPointer(arrayRef) ?: return@doWithState

        val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
        val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }
        val arrayTypeConstraints: List<Pair<UBoolExpr, (JcState) -> Unit>> = possibleArrayTypes.map { type ->
            val length = readArrayLength(arrayRef, type)
            memory.types.evalIsSubtype(arrayRef, type) to { state ->
                if (addLengthBounds(length) != null) {
                    state.skipMethodInvocationWithValue(methodCall, length)
                }
            }
        }

        val unknownArrayType = ctx.mkAnd(arrayTypeConstraints.map { ctx.mkNot(it.first) })
        val exceptionalState = unknownArrayType to allocateException(ctx.illegalArgumentExceptionType)
        scope.forkMulti(arrayTypeConstraints + exceptionalState)
    }

    // TODO: move to java-stdlib-appoximations
    private fun JcExprResolver.resolveGetArrayElem(
        methodCall: JcMethodCall,
        arrayRef: UHeapRef,
        index: UExpr<USizeSort>,
    ) = scope.doWithState {
        checkNullPointer(arrayRef) ?: return@doWithState

        val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
        val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }
        val arrayTypeConstraints: List<Pair<UBoolExpr, (JcState) -> Unit>> = possibleArrayTypes.map { type ->
            val length = readArrayLength(arrayRef, type)
            memory.types.evalIsSubtype(arrayRef, type) to { state ->
                if (addLengthBounds(length) != null) {
                    if (checkArrayIndex(index, length) != null) {
                        val arrayDescriptor = ctx.arrayDescriptorOf(type)
                        val elementType = requireNotNull(type.ifArrayGetElementType)
                        val cellSort = ctx.typeToSort(elementType)
                        val lvalue = UArrayIndexLValue(cellSort, arrayRef, index, arrayDescriptor)
                        val elem = memory.read(lvalue)
                        state.skipMethodInvocationWithValue(methodCall, elem)
                    }
                }
            }
        }

        val unknownArrayType = ctx.mkAnd(arrayTypeConstraints.map { ctx.mkNot(it.first) })
        val exceptionalState = unknownArrayType to allocateException(ctx.illegalArgumentExceptionType)
        scope.forkMulti(arrayTypeConstraints + exceptionalState)
    }

    // TODO: move to java-stdlib-appoximations
    private fun JcExprResolver.resolveSetArrayElem(
        methodCall: JcMethodCall,
        arrayRef: UHeapRef,
        index: UExpr<USizeSort>,
        value: UExpr<out USort>,
    ) = scope.doWithState {
        checkNullPointer(arrayRef) ?: return@doWithState

        val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
        val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }
        val arrayTypeConstraints: List<Pair<UBoolExpr, (JcState) -> Unit>> = possibleArrayTypes.map { type ->
            val length = readArrayLength(arrayRef, type)
            memory.types.evalIsSubtype(arrayRef, type) to { state ->
                if (addLengthBounds(length) != null) {
                    if (checkArrayIndex(index, length) != null) {
                        val arrayDescriptor = ctx.arrayDescriptorOf(type)
                        val elementType = requireNotNull(type.ifArrayGetElementType)
                        val cellSort = ctx.typeToSort(elementType)
                        val lvalue = UArrayIndexLValue(cellSort, arrayRef, index, arrayDescriptor)
                        memory.write(lvalue, value)
                        state.skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                    }
                }
            }
        }

        val unknownArrayType = ctx.mkAnd(arrayTypeConstraints.map { ctx.mkNot(it.first) })
        val exceptionalState = unknownArrayType to allocateException(ctx.illegalArgumentExceptionType)
        scope.forkMulti(arrayTypeConstraints + exceptionalState)
    }

    private fun approximateUnsafeVirtualMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        // Array offset is usually the same on various JVM
        if (method.name == "arrayBaseOffset0") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.mkBv(16, ctx.integerSort))
            }
            return true
        }

        if (method.name == "arrayIndexScale0") {
            val primitiveArrayScale = mapOf(
                ctx.cp.boolean to 1,
                ctx.cp.byte to Byte.SIZE_BYTES,
                ctx.cp.short to Short.SIZE_BYTES,
                ctx.cp.int to Int.SIZE_BYTES,
                ctx.cp.long to Long.SIZE_BYTES,
                ctx.cp.char to Char.SIZE_BYTES,
                ctx.cp.float to Float.SIZE_BYTES,
                ctx.cp.double to Double.SIZE_BYTES,
            )

            val primitiveArrayRefScale = primitiveArrayScale.mapKeys { (type, _) ->
                exprResolver.simpleValueResolver.resolveClassRef(ctx.cp.arrayTypeOf(type))
            }

            val arrayTypeRef = arguments.last().asExpr(ctx.addressSort)

            val result = primitiveArrayRefScale.entries.fold(
                // All non-primitive (object) arrays usually have 4 bytes scale on various JVM
                ctx.mkBv(4, ctx.integerSort) as UExpr<UBv32Sort>
            ) { res, (typeRef, scale) ->
                ctx.mkIte(ctx.mkHeapRefEq(arrayTypeRef, typeRef), ctx.mkBv(scale, ctx.integerSort), res)
            }

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateSystemStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "arraycopy") {
            // Object src, int srcPos, Object dest, int destPos, int length
            val (srcRef, srcPos, dstRef, dstPos, length) = arguments
            with(ctx) {
                exprResolver.resolveArrayCopy(
                    methodCall = methodCall,
                    srcRef = srcRef.asExpr(addressSort),
                    srcPos = srcPos.asExpr(sizeSort),
                    dstRef = dstRef.asExpr(addressSort),
                    dstPos = dstPos.asExpr(sizeSort),
                    length = length.asExpr(sizeSort),
                )
            }
            return true
        }

        return false
    }

    private fun JcState.arrayContentEquals(
        firstArray: UHeapRef,
        secondArray: UHeapRef,
        firstLength: UExpr<USizeSort>,
        secondLength: UExpr<USizeSort>,
        arrayType: JcArrayType,
    ): UBoolExpr? = with(ctx) {
        val arrayDesciptor = arrayDescriptorOf(arrayType)
        val elementType = arrayType.elementType
        val elementSort = typeToSort(elementType)

        val concreteLength =
            getIntValue(firstLength)
                ?: getIntValue(secondLength)
                ?: return@with null

        val arrayEquals = List(concreteLength) {
            val idx = mkSizeExpr(it)
            val first = memory.readArrayIndex(firstArray, idx, arrayDesciptor, elementSort)
            val second =
                memory.readArrayIndex(secondArray, idx, arrayDesciptor, elementSort)
            mkEq(first, second)
        }

        return@with mkAnd(arrayEquals)
    }

    private fun JcState.arrayEquals(methodCall: JcMethodCall, firstArray: UHeapRef, secondArray: UHeapRef) = with(ctx) {
        val possibleElementTypes = primitiveTypes + cp.objectType
        val possibleArrayTypes = possibleElementTypes.map { cp.arrayTypeOf(it) }

        val branches = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()
        var typeDiffersConstraint: UBoolExpr = trueExpr

        val arrayRefsEqual = mkEq(firstArray, secondArray)
        val oneArrayIsNull = mkOr(mkEq(firstArray, nullRef), mkEq(secondArray, nullRef))
        branches += arrayRefsEqual to { state ->
            state.skipMethodInvocationAndBoxIfNeeded(methodCall, cp.boolean, trueExpr)
        }
        branches += mkAnd(mkNot(arrayRefsEqual), oneArrayIsNull) to { state ->
            state.skipMethodInvocationAndBoxIfNeeded(methodCall, cp.boolean, falseExpr)
        }
        val needToCheckContent = mkAnd(mkNot(arrayRefsEqual), mkNot(oneArrayIsNull))
        for (arrayType in possibleArrayTypes) {
            val typeConstraint = scope.calcOnState {
                mkAnd(
                    memory.types.evalIsSubtype(firstArray, arrayType),
                    memory.types.evalIsSubtype(secondArray, arrayType)
                )
            }
            typeDiffersConstraint = mkAnd(typeDiffersConstraint, mkNot(typeConstraint))
            val arrayDesciptor = arrayDescriptorOf(arrayType)
            val firstLength = memory.readArrayLength(firstArray, arrayDesciptor, sizeSort)
            val secondLength = memory.readArrayLength(secondArray, arrayDesciptor, sizeSort)
            val lengthsEqual = mkEq(firstLength, secondLength)

            branches += mkAnd(needToCheckContent, typeConstraint, mkNot(lengthsEqual)) to { state ->
                state.skipMethodInvocationAndBoxIfNeeded(methodCall, cp.boolean, falseExpr)
            }

            branches += mkAnd(needToCheckContent, typeConstraint, lengthsEqual) to { state ->
                val checkResult = state.arrayContentEquals(firstArray, secondArray, firstLength, secondLength, arrayType)
                if (checkResult == null) {
                    // Unable to check
                    state.skipMethodInvocationWithValue(methodCall, nullRef)
                } else {
                    state.skipMethodInvocationAndBoxIfNeeded(methodCall, cp.boolean, checkResult)
                }
            }
        }

        branches += typeDiffersConstraint to { state ->
            state.skipMethodInvocationAndBoxIfNeeded(methodCall, cp.boolean, falseExpr)
        }

        scope.forkMulti(branches)
    }

    private sealed interface StringConcatElement
    private data class StringConcatStrElement(val str: String) : StringConcatElement
    private data class StringConcatRefElement(val ref: UHeapRef) : StringConcatElement

    private fun approximateStringConcat(methodCallInst: JcDynamicMethodCallInst): Boolean = with(methodCallInst) {
        if (dynamicCall.method.name == "makeConcatWithConstants") {
            val concatUtil = ctx.cp.findClassOrNull("org.usvm.api.internal.StringConcatUtil")
                ?.declaredMethods
                ?.single { it.name == "concat" }
                ?: return false

            val recipe = (dynamicCall.bsmArgs.firstOrNull() as? BsmStringArg)?.value
                ?: error("Unexpected dynamic call: $methodCallInst")

            val elements = parseStringConcatRecipe(recipe, dynamicCall.bsmArgs.drop(1), arguments)

            val elementRefs = elements.map {
                when (it) {
                    is StringConcatRefElement -> it.ref
                    is StringConcatStrElement -> JcStringConstant(it.str, ctx.stringType)
                        .accept(exprResolver.simpleValueResolver)
                        .asExpr(ctx.addressSort)
                }
            }

            val elementArray = scope.calcOnState {
                val arrayType = ctx.cp.arrayTypeOf(ctx.cp.objectType)
                val descriptor = ctx.arrayDescriptorOf(arrayType)
                val arrayHeapRef = memory.allocConcrete(arrayType)
                memory.initializeArray(
                    arrayHeapRef,
                    descriptor,
                    ctx.addressSort,
                    ctx.sizeSort,
                    elementRefs.asSequence()
                )
                arrayHeapRef
            }

            scope.doWithState {
                newStmt(JcConcreteMethodCallInst(location, concatUtil, listOf(elementArray), returnSite))
            }

            return true
        }

        return false
    }

    private fun parseStringConcatRecipe(
        recipe: String,
        bsmArgs: List<BsmArg>,
        callArgs: List<UExpr<*>>,
    ): List<StringConcatElement> {
        val elements = mutableListOf<StringConcatElement>()

        val acc = StringBuilder()

        var constCount = 0
        var argsCount = 0

        for (recipeCh in recipe) {
            when (recipeCh) {
                '\u0002' -> {
                    // Accumulate constant args along with any constants encoded
                    // into the recipe
                    val constant = bsmArgs.getOrNull(constCount++)
                        ?: error("Incorrect dynamic call")

                    val constantValue = when (constant) {
                        is BsmDoubleArg -> constant.value.toString()
                        is BsmFloatArg -> constant.value.toString()
                        is BsmIntArg -> constant.value.toString()
                        is BsmLongArg -> constant.value.toString()
                        is BsmStringArg -> constant.value
                        is BsmHandle,
                        is BsmMethodTypeArg,
                        is BsmTypeArg -> error("Incorrect dynamic call constant")
                    }

                    acc.append(constantValue)
                }

                '\u0001' -> {
                    // Flush any accumulated characters into a constant
                    if (acc.isNotEmpty()) {
                        elements.add(StringConcatStrElement(acc.toString()))
                        acc.setLength(0)
                    }

                    val argRef = callArgs.getOrNull(argsCount++) ?: error("Incorrect dynamic call arg")

                    if (argRef.sort != ctx.addressSort) {
                        // todo: primitive args
                        continue
                    }

                    elements.add(StringConcatRefElement(argRef.asExpr(ctx.addressSort)))
                }

                else -> {
                    // Not a special character, this is a constant embedded into
                    // the recipe itself.
                    acc.append(recipeCh)
                }
            }
        }

        // Flush the remaining characters as constant:
        if (acc.isNotEmpty()) {
            elements.add(StringConcatStrElement(acc.toString()))
        }

        return elements
    }

    private fun JcExprResolver.resolveArrayCopy(
        methodCall: JcMethodCall,
        srcRef: UHeapRef,
        srcPos: UExpr<USizeSort>,
        dstRef: UHeapRef,
        dstPos: UExpr<USizeSort>,
        length: UExpr<USizeSort>
    ) {
        checkNullPointer(srcRef) ?: return
        checkNullPointer(dstRef) ?: return

        val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
        val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }

        val arrayTypeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()
        possibleArrayTypes.forEach { type ->
            addArrayCopyForType(
                methodCall, arrayTypeConstraintsWithBlockOnStates, type,
                srcRef, srcPos,
                dstRef, dstPos,
                length
            )
        }

        val arrayTypeConstraints = possibleArrayTypes.map { type ->
            scope.calcOnState {
                ctx.mkAnd(
                    memory.types.evalIsSubtype(srcRef, type),
                    memory.types.evalIsSubtype(dstRef, type)
                )
            }
        }
        val unknownArrayType = ctx.mkAnd(arrayTypeConstraints.map { ctx.mkNot(it) })
        arrayTypeConstraintsWithBlockOnStates += unknownArrayType to allocateException(ctx.arrayStoreExceptionType)

        scope.forkMulti(arrayTypeConstraintsWithBlockOnStates)
    }

    private fun approximateObjectClone(methodCall: JcMethodCall): Boolean {
        val instance = methodCall.arguments.first().asExpr(ctx.addressSort)
        val type = scope.calcOnState { memory.types.getTypeStream(instance).commonSuperType }
        if (type is JcArrayType) {
            exprResolver.resolveArrayClone(methodCall, instance, type)
            return true
        }

        if (methodCall is JcConcreteMethodCallInst) {
            type as JcClassType
            exprResolver.resolveObjectClone(methodCall, instance, type)
            return true
        }

        return false
    }

    private fun JcExprResolver.resolveObjectClone(
        methodCall: JcMethodCall,
        instance: UHeapRef,
        type: JcClassType,
    ) = with(ctx) {
        scope.doWithState {
            checkNullPointer(instance) ?: return@doWithState

            val clonedRef = memory.allocHeapRef(type, useStaticAddress = useStaticAddressForAllocation())
            for (field in type.allInstanceFields) {
                val fieldSort = ctx.typeToSort(field.type)
                val jcField = field.field
                val fieldValue = memory.readField(instance, jcField, fieldSort)
                memory.writeField(clonedRef, jcField, fieldSort, fieldValue, ctx.trueExpr)
            }

            skipMethodInvocationWithValue(methodCall, clonedRef)
        }
    }

    private fun JcExprResolver.resolveArrayClone(
        methodCall: JcMethodCall,
        instance: UHeapRef,
        arrayType: JcArrayType,
    ) = with(ctx) {
        scope.doWithState {
            checkNullPointer(instance) ?: return@doWithState

            val arrayDescriptor = arrayDescriptorOf(arrayType)
            val elementType = requireNotNull(arrayType.ifArrayGetElementType)

            val lengthRef = UArrayLengthLValue(instance, arrayDescriptor, sizeSort)
            val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }

            val arrayRef = memory.allocHeapRef(arrayType, useStaticAddress = useStaticAddressForAllocation())
            memory.write(UArrayLengthLValue(arrayRef, arrayDescriptor, sizeSort), length)

            // It is very important to use arrayDescriptor here but not elementType correspondingly as in creating
            // new arrays
            memory.memcpy(
                srcRef = instance,
                dstRef = arrayRef,
                arrayDescriptor,
                elementSort = typeToSort(elementType),
                fromSrc = mkBv(0),
                fromDst = mkBv(0),
                length
            )

            skipMethodInvocationWithValue(methodCall, arrayRef)
        }
    }

    private fun JcExprResolver.addArrayCopyForType(
        methodCall: JcMethodCall,
        branches: MutableList<Pair<UBoolExpr, (JcState) -> Unit>>,
        type: JcArrayType,
        srcRef: UHeapRef,
        srcPos: UExpr<USizeSort>,
        dstRef: UHeapRef,
        dstPos: UExpr<USizeSort>,
        length: UExpr<USizeSort>
    ) = with(ctx) {
        val arrayDescriptor = arrayDescriptorOf(type)
        val elementType = requireNotNull(type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        val arrayTypeConstraint = scope.calcOnState {
            mkAnd(
                memory.types.evalIsSubtype(srcRef, type),
                memory.types.evalIsSubtype(dstRef, type)
            )
        }

        val srcLengthRef = UArrayLengthLValue(srcRef, arrayDescriptor, sizeSort)
        val srcLength = scope.calcOnState { memory.read(srcLengthRef) }

        val dstLengthRef = UArrayLengthLValue(dstRef, arrayDescriptor, sizeSort)
        val dstLength = scope.calcOnState { memory.read(dstLengthRef) }

        val indexBoundsCheck = mkAnd(
            mkBvSignedLessOrEqualExpr(mkBv(0), srcPos),
            mkBvSignedLessOrEqualExpr(mkBv(0), dstPos),
            mkBvSignedLessOrEqualExpr(mkBv(0), length),
            mkBvSignedLessOrEqualExpr(mkBvAddExpr(srcPos, length), srcLength),
            mkBvSignedLessOrEqualExpr(mkBvAddExpr(dstPos, length), dstLength),
        )

        val indexOutOfBoundsConstraint = arrayTypeConstraint and indexBoundsCheck.not()
        branches += indexOutOfBoundsConstraint to allocateException(arrayIndexOutOfBoundsExceptionType)

        val arrayCopySuccessConstraint = arrayTypeConstraint and indexBoundsCheck
        val arrayCopyBlock = { state: JcState ->
            state.memory.memcpy(
                srcRef = srcRef,
                dstRef = dstRef,
                type = arrayDescriptor,
                elementSort = cellSort,
                fromSrc = srcPos,
                fromDst = dstPos,
                length = length
            )

            state.skipMethodInvocationWithValue(methodCall, ctx.voidValue)
        }

        branches += arrayCopySuccessConstraint to arrayCopyBlock
    }

    private fun approximateStringUtf16StaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        // Use common property value as approximation
        if (method.name == "isBigEndian") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.falseExpr)
            }
            return true
        }
        return false
    }

    private fun approximateFloatStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "floatToRawIntBits") {
            val value = arguments.single().asExpr(ctx.floatSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.integerSort)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        if (method.name == "intBitsToFloat") {
            val value = arguments.single().asExpr(ctx.integerSort)
            val result = mkFpFromBits(ctx.floatSort, value)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateDoubleStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "doubleToRawLongBits") {
            val value = arguments.single().asExpr(ctx.doubleSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.longSort)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        if (method.name == "longBitsToDouble") {
            val value = arguments.single().asExpr(ctx.longSort)
            val result = mkFpFromBits(ctx.doubleSort, value)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun skipMethodIfThrowable(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.enclosingClass.name == "java.lang.Throwable") {
            // We assume that methods of java.lang.Throwable are not really required to be analysed and can be simply mocked
            mockMethod(scope, methodCall, applicationGraph)
            return true
        }

        return false
    }

    private fun <Fp : UFpSort> mkFpFromBits(sort: Fp, bits: UExpr<out UBvSort>): UExpr<Fp> = with(ctx) {
        val exponentBits = sort.exponentBits.toInt()
        val size = bits.sort.sizeBits.toInt()

        val sign = mkBvExtractExpr(size - 1, size - 1, bits)
        val exponent = mkBvExtractExpr(size - 2, size - exponentBits - 1, bits)
        val significand = mkBvExtractExpr(size - exponentBits - 2, 0, bits)

        mkFpFromBvExpr(sign.uncheckedCast(), exponent, significand)
    }

    private fun JcMethod.hasVoidReturnType(): Boolean =
        returnType.typeName == ctx.cp.void.typeName

    private val symbolicListType: JcType by lazy {
        checkNotNull(usvmApiSymbolicList).toType()
    }

    private val symbolicListImplType: JcType by lazy {
        checkNotNull(usvmApiSymbolicListImpl).toType()
    }

    private val symbolicMapType: JcType by lazy {
        checkNotNull(usvmApiSymbolicMap).toType()
    }

    private val symbolicMapImplType: JcType by lazy {
        checkNotNull(usvmApiSymbolicMapImpl).toType()
    }

    private val symbolicIdentityMapType: JcType by lazy {
        checkNotNull(usvmApiSymbolicIdentityMap).toType()
    }

    private val usvmApiEngineMethods: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        buildMap {
            dispatchUsvmApiMethod(Engine::assume) {
                val arg = it.arguments.single().asExpr(ctx.booleanSort)
                scope.assert(arg)?.let { ctx.voidValue }
            }
            dispatchUsvmApiMethod(Engine::assumeSymbolic) {
                val instance = it.arguments[0].asExpr(ctx.addressSort)
                val condition = it.arguments[1].asExpr(ctx.booleanSort)
                foldHeapRefWithStaticAsConcrete<Unit?>(
                    ref = instance,
                    initial = Unit,
                    initialGuard = ctx.trueExpr,
                    ignoreNullRefs = true,
                    collapseHeapRefs = true,
                    blockOnConcrete = { _, _ -> Unit },
                    blockOnSymbolic = { acc, ref -> scope.assert(ctx.mkImplies(ref.guard, condition)) ?: acc }
                )?.let { ctx.voidValue }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicBoolean) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.booleanSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicByte) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.byteSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicChar) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.charSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicShort) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.shortSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicInt) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.integerSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicLong) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.longSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicFloat) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.floatSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicDouble) {
                scope.calcOnState { makeSymbolicPrimitive(ctx.doubleSort) }
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicBooleanArray) {
                makeSymbolicArray(ctx.cp.boolean, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicByteArray) {
                makeSymbolicArray(ctx.cp.byte, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicCharArray) {
                makeSymbolicArray(ctx.cp.char, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicShortArray) {
                makeSymbolicArray(ctx.cp.short, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicIntArray) {
                makeSymbolicArray(ctx.cp.int, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicLongArray) {
                makeSymbolicArray(ctx.cp.long, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicFloatArray) {
                makeSymbolicArray(ctx.cp.float, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::makeSymbolicDoubleArray) {
                makeSymbolicArray(ctx.cp.double, it.arguments.single())
            }
            dispatchUsvmApiMethod(Engine::typeEquals) {
                val (ref0, ref1) = it.arguments.map { it.asExpr(ctx.addressSort) }
                scope.calcOnState { objectTypeEquals(ref0, ref1) }
            }
            dispatchUsvmApiMethod(Engine::typeIs) {
                val (ref, classRef) = it.arguments.map { it.asExpr(ctx.addressSort) }
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.calcOnState { objectTypeEquals(ref, classRefTypeRepresentative) }
            }
            dispatchUsvmApiMethod(Engine::typeIsArray) {
                val ref = it.arguments[0].asExpr(ctx.addressSort)
                scope.calcOnState {
                    val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
                    val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }
                    possibleArrayTypes.map { type -> memory.types.evalIsSubtype(ref, type) }.reduce(ctx::mkOr)
                }
            }
            dispatchUsvmApiMethod(Engine::typeIsSubtype) {
                val (ref, classRef) = it.arguments.map { it.asExpr(ctx.addressSort) }
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.calcOnState { objectTypeSubtype(ref, classRefTypeRepresentative) }
            }
            dispatchUsvmApiMethod(Engine::arrayElementType) {
                val ref = it.arguments[0].asExpr(ctx.addressSort)
                scope.calcOnState {
                    mapTypeStreamNotNull(ref) { _, types ->
                        val type = types.singleOrNull() ?: return@mapTypeStreamNotNull null
                        if (type !is JcArrayType) {
                            return@mapTypeStreamNotNull ctx.nullRef
                        }
                        exprResolver.simpleValueResolver.resolveClassRef(type.elementType)
                    } ?: run {
                        val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType + ctx.stringType
                        val mock = scope.makeSymbolicRef(ctx.classType) ?: error("unable to crate mock for Class type")
                        val defaultCase = ctx.mkIte(
                            memory.types.evalIsSubtype(ref, ctx.cp.objectType),
                            trueBranch = mock,
                            falseBranch = ctx.nullRef
                        )
                        possibleElementTypes.fold(defaultCase) { acc, type ->
                            val cond = memory.types.evalTypeEquals(ref, ctx.cp.arrayTypeOf(type))
                            val classRef = exprResolver.simpleValueResolver.resolveClassRef(type)
                            ctx.mkIte(cond, classRef, acc)
                        }
                    }
                }
            }
            dispatchMkRef(Engine::makeSymbolic) {
                val classRef = it.arguments.single().asExpr(ctx.addressSort)
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.makeSymbolicRefWithSameType(classRefTypeRepresentative)
            }
            dispatchMkRef(Engine::makeNullableSymbolic) {
                val classRef = it.arguments.single().asExpr(ctx.addressSort)
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.makeNullableSymbolicRefWithSameType(classRefTypeRepresentative)
            }
            dispatchMkRef(Engine::makeSymbolicSubtype) {
                val classRef = it.arguments.single().asExpr(ctx.addressSort)
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.makeSymbolicRefSubtype(classRefTypeRepresentative)
            }
            dispatchMkRef(Engine::makeNullableSymbolicSubtype) {
                val classRef = it.arguments.single().asExpr(ctx.addressSort)
                val classRefTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                }
                scope.makeNullableSymbolicRefSubtype(classRefTypeRepresentative)
            }
            dispatchMkRef2(Engine::makeSymbolicArray) {
                val (elementClassRefExpr, sizeExpr) = it.arguments
                val elementClassRef = elementClassRefExpr.asExpr(ctx.addressSort)
                val elementTypeRepresentative = scope.calcOnState {
                    memory.read(UFieldLValue(ctx.addressSort, elementClassRef, ctx.classTypeSyntheticField))
                }

                if (elementTypeRepresentative is UConcreteHeapRef) {
                    val type = scope.calcOnState { memory.types.getTypeStream(elementTypeRepresentative).first() }
                    makeSymbolicArray(type, sizeExpr)
                } else {
                    // todo: correct type instead of object
                    makeSymbolicArray(ctx.cp.objectType, sizeExpr)
                }
            }
            dispatchUsvmApiMethod(Engine::arrayEquals) {
                val first = it.arguments[0].asExpr(ctx.addressSort)
                val second = it.arguments[1].asExpr(ctx.addressSort)
                scope.doWithState { arrayEquals(it, first, second) }
                null
            }
            dispatchMkList(Engine::makeSymbolicList) {
                scope.calcOnState { mkSymbolicList(symbolicListType) }
            }
            dispatchMkList(Engine::makeFullySymbolicList) {
                scope.makeSymbolicRef(symbolicListImplType)
            }
            dispatchMkMap(Engine::makeSymbolicMap) {
                scope.calcOnState { mkSymbolicObjectMap(symbolicMapType) }
            }
            dispatchMkMap(Engine::makeFullySymbolicMap) {
                scope.makeSymbolicRef(symbolicMapImplType)
            }
            dispatchMkIdMap(Engine::makeSymbolicIdentityMap) {
                scope.calcOnState { mkSymbolicObjectMap(symbolicIdentityMapType) }
            }
        }
    }

    private val usvmApiListMethods: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        buildMap {
            dispatchUsvmApiMethod(SymbolicList<*>::size) {
                val listRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicListSize(listRef, symbolicListType)
                }
            }
            dispatchUsvmApiMethod(SymbolicList<*>::get) {
                val (rawListRef, rawIdx) = it.arguments
                val listRef = rawListRef.asExpr(ctx.addressSort)
                val idx = rawIdx.asExpr(ctx.sizeSort)

                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicListGet(listRef, idx, symbolicListType, ctx.addressSort)
                }
            }
            dispatchUsvmApiMethod(SymbolicList<*>::set) {
                val (rawListRef, rawIdx, rawValueRef) = it.arguments
                val listRef = rawListRef.asExpr(ctx.addressSort)
                val idx = rawIdx.asExpr(ctx.sizeSort)
                val valueRef = rawValueRef.asExpr(ctx.addressSort)

                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicListSet(listRef, symbolicListType, ctx.addressSort, idx, valueRef)
                    ctx.voidValue
                }
            }
            dispatchUsvmApiMethod(SymbolicList<*>::insert) {
                val (rawListRef, rawIdx, rawValueRef) = it.arguments
                val listRef = rawListRef.asExpr(ctx.addressSort)
                val idx = rawIdx.asExpr(ctx.sizeSort)
                val valueRef = rawValueRef.asExpr(ctx.addressSort)

                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicListInsert(listRef, symbolicListType, ctx.addressSort, idx, valueRef)
                    ctx.voidValue
                }
            }
            dispatchUsvmApiMethod(SymbolicList<*>::remove) {
                val (rawListRef, rawIdx) = it.arguments
                val listRef = rawListRef.asExpr(ctx.addressSort)
                val idx = rawIdx.asExpr(ctx.sizeSort)

                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null

                val result = scope.calcOnState {
                    symbolicListRemove(listRef, symbolicListType, ctx.addressSort, idx)
                    ctx.voidValue
                }

                scope.ensureListSizeCorrect(listRef, symbolicListType) ?: return@dispatchUsvmApiMethod null

                result
            }
            dispatchUsvmApiMethod(SymbolicList<*>::copy) {
                val (listRef, dstListRef, srcFromIdx, dstFromIdx, length) = it.arguments

                scope.ensureListSizeCorrect(listRef.asExpr(ctx.addressSort), symbolicListType)
                    ?: return@dispatchUsvmApiMethod null

                scope.calcOnState {
                    symbolicListCopyRange(
                        srcRef = listRef.asExpr(ctx.addressSort),
                        dstRef = dstListRef.asExpr(ctx.addressSort),
                        listType = symbolicListType,
                        sort = ctx.addressSort,
                        srcFrom = srcFromIdx.asExpr(ctx.sizeSort),
                        dstFrom = dstFromIdx.asExpr(ctx.sizeSort),
                        length = length.asExpr(ctx.sizeSort),
                    )
                    ctx.voidValue
                }
            }
        }
    }

    private fun bindUsvmApiIdMapMethods(symbolicMapType: JcType): Map<String, (JcMethodCall) -> UExpr<*>?> =
        buildMap {
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::size) {
                val mapRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapSize(mapRef, symbolicMapType)
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::get) {
                val (mapRef, keyRef) = it.arguments.map { it.asExpr(ctx.addressSort) }

                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapGet(mapRef, keyRef, symbolicMapType, ctx.addressSort)
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::set) {
                val (mapRef, keyRef, valueRef) = it.arguments.map { it.asExpr(ctx.addressSort) }

                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapPut(mapRef, keyRef, valueRef, symbolicMapType, ctx.addressSort)
                    ctx.voidValue
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::remove) {
                val (mapRef, keyRef) = it.arguments.map { it.asExpr(ctx.addressSort) }

                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapRemove(mapRef, keyRef, symbolicMapType)
                    ctx.voidValue
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::containsKey) {
                val (mapRef, keyRef) = it.arguments.map { it.asExpr(ctx.addressSort) }

                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapContains(mapRef, keyRef, symbolicMapType)
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::anyKey) {
                val mapRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.ensureObjectMapSizeCorrect(mapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.calcOnState {
                    symbolicObjectMapAnyKey(mapRef, symbolicMapType)
                }
            }
            dispatchUsvmApiMethod(SymbolicIdentityMap<*, *>::merge) {
                val (dstMapRef, srcMapRef) = it.arguments.map { it.asExpr(ctx.addressSort) }

                scope.ensureObjectMapSizeCorrect(dstMapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null
                scope.ensureObjectMapSizeCorrect(srcMapRef, symbolicMapType) ?: return@dispatchUsvmApiMethod null

                scope.calcOnState {
                    symbolicObjectMapMergeInto(dstMapRef, srcMapRef, symbolicMapType, ctx.addressSort)
                    ctx.voidValue
                }
            }
        }

    private val usvmApiIdMapMethods: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        bindUsvmApiIdMapMethods(symbolicIdentityMapType)
    }

    private val usvmApiMapMethods: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        // TODO: use map with `equals` instead of identity
        bindUsvmApiIdMapMethods(symbolicMapType)
    }

    private fun approximateUsvmApiEngineStaticMethod(methodCall: JcMethodCall) {
        if (methodCall.method.isClassInitializer) {
            scope.doWithState { skipMethodInvocationWithValue(methodCall, ctx.voidValue) }
            return
        }

        val methodApproximation = usvmApiEngineMethods[methodCall.method.name]
            ?: error("Unexpected engine api method: ${methodCall.method.name}")
        val result = methodApproximation(methodCall) ?: return
        scope.doWithState { skipMethodInvocationWithValue(methodCall, result) }
    }

    private fun approximateUsvmSymbolicListMethod(methodCall: JcMethodCall) {
        val methodApproximation = usvmApiListMethods[methodCall.method.name]
            ?: error("Unexpected list api method: ${methodCall.method.name}")
        val result = methodApproximation(methodCall) ?: return
        scope.doWithState { skipMethodInvocationWithValue(methodCall, result) }
    }

    private fun approximateUsvmSymbolicMapMethod(methodCall: JcMethodCall) {
        val methodApproximation = usvmApiMapMethods[methodCall.method.name]
            ?: error("Unexpected map api method: ${methodCall.method.name}")
        val result = methodApproximation(methodCall) ?: return
        scope.doWithState { skipMethodInvocationWithValue(methodCall, result) }
    }

    private fun approximateUsvmSymbolicIdMapMethod(methodCall: JcMethodCall) {
        val methodApproximation = usvmApiIdMapMethods[methodCall.method.name]
            ?: error("Unexpected identity map api method: ${methodCall.method.name}")
        val result = methodApproximation(methodCall) ?: return
        scope.doWithState { skipMethodInvocationWithValue(methodCall, result) }
    }

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchUsvmApiMethod(
        apiMethod: KFunction<*>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) {
        val methodName = apiMethod.javaName
        this[methodName] = body
    }

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMkRef(
        apiMethod: KFunction1<Nothing, Any>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) = dispatchUsvmApiMethod(apiMethod, body)

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMkRef2(
        apiMethod: KFunction2<Nothing, Nothing, Array<Any>>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) = dispatchUsvmApiMethod(apiMethod, body)

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMkList(
        apiMethod: KFunction0<SymbolicList<Any>>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) = dispatchUsvmApiMethod(apiMethod, body)

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMkMap(
        apiMethod: KFunction0<SymbolicMap<Any, Any>>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) = dispatchUsvmApiMethod(apiMethod, body)

    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMkIdMap(
        apiMethod: KFunction0<SymbolicIdentityMap<Any, Any>>,
        body: (JcMethodCall) -> UExpr<*>?,
    ) = dispatchUsvmApiMethod(apiMethod, body)

    private fun makeSymbolicArray(elementType: JcType, size: UExpr<*>): UHeapRef? {
        val sizeValue = size.asExpr(ctx.sizeSort)
        val arrayType = ctx.cp.arrayTypeOf(elementType)

        val address = scope.makeSymbolicRef(arrayType) ?: return null

        val arrayDescriptor = ctx.arrayDescriptorOf(arrayType)
        val lengthRef = UArrayLengthLValue(address, arrayDescriptor, ctx.sizeSort)
        scope.doWithState {
            memory.write(lengthRef, sizeValue)
        }

        return address
    }
}
