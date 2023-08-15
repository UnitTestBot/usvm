package org.usvm.machine.interpreter

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import io.ksmt.utils.uncheckedCast
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.cfg.JcAddExpr
import org.jacodb.api.cfg.JcAndExpr
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcBinaryExpr
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcByte
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcChar
import org.jacodb.api.cfg.JcClassConstant
import org.jacodb.api.cfg.JcCmpExpr
import org.jacodb.api.cfg.JcCmpgExpr
import org.jacodb.api.cfg.JcCmplExpr
import org.jacodb.api.cfg.JcDivExpr
import org.jacodb.api.cfg.JcDouble
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcExprVisitor
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcFloat
import org.jacodb.api.cfg.JcGeExpr
import org.jacodb.api.cfg.JcGtExpr
import org.jacodb.api.cfg.JcInstanceOfExpr
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcLambdaExpr
import org.jacodb.api.cfg.JcLeExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcLong
import org.jacodb.api.cfg.JcLtExpr
import org.jacodb.api.cfg.JcMethodConstant
import org.jacodb.api.cfg.JcMethodType
import org.jacodb.api.cfg.JcMulExpr
import org.jacodb.api.cfg.JcNegExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcOrExpr
import org.jacodb.api.cfg.JcPhiExpr
import org.jacodb.api.cfg.JcRemExpr
import org.jacodb.api.cfg.JcShlExpr
import org.jacodb.api.cfg.JcShort
import org.jacodb.api.cfg.JcShrExpr
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcSubExpr
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcUshrExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.cfg.JcXorExpr
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.ifArrayGetElementType
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.void
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.FieldInfo
import org.usvm.UArrayIndexLValue
import org.usvm.UArrayLengthLValue
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFieldLValue
import org.usvm.UFpSort
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.URegisterLValue
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.operator.JcBinaryOperator
import org.usvm.machine.operator.JcUnaryOperator
import org.usvm.machine.operator.ensureBvExpr
import org.usvm.machine.operator.mkNarrow
import org.usvm.machine.operator.wideTo32BitsIfNeeded
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.exitWithValue
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.util.extractJcRefType

/**
 * An expression resolver based on JacoDb 3-address code. A result of resolving is `null`, iff
 * the original state is dead, as stated in [JcStepScope].
 */
class JcExprResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    private val localToIdx: (JcMethod, JcLocal) -> Int,
    private val mkTypeRef: (JcType, JcState) -> UConcreteHeapRef,
    private val mkStringConstRef: (String, JcState) -> UConcreteHeapRef,
    private val invokeResolver: JcInvokeResolver,
    private val hardMaxArrayLength: Int = 1_500, // TODO: move to options
) : JcExprVisitor<UExpr<out USort>?> {
    /**
     * Resolves the [expr] and casts it to match the desired [type].
     *
     * @return a symbolic expression, with the sort corresponding to the [type].
     */
    fun resolveJcExpr(expr: JcExpr, type: JcType = expr.type): UExpr<out USort>? =
        if (expr.type != type) {
            resolveCast(expr, type)
        } else {
            expr.accept(this)
        }

    fun resolveJcNotNullRefExpr(expr: JcExpr, type: JcType): UHeapRef? {
        check(type is JcRefType) { "Non ref type: $expr" }

        val refExpr = resolveJcExpr(expr, type)?.asExpr(ctx.addressSort) ?: return null
        checkNullPointer(refExpr) ?: return null
        return refExpr
    }

    /**
     * Builds a [ULValue] from a [value].
     *
     * @return `null` if the symbolic state is dead, as stated in the [JcStepScope] documentation.
     *
     * @see JcStepScope
     */
    fun resolveLValue(value: JcValue): ULValue? =
        when (value) {
            is JcFieldRef -> resolveFieldRef(value.instance, value.field)
            is JcArrayAccess -> resolveArrayAccess(value.array, value.index)
            is JcLocal -> resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    override fun visitExternalJcExpr(expr: JcExpr): UExpr<out USort> {
        error("Unexpected expression: $expr")
    }

    // region binary operators

    override fun visitJcAddExpr(expr: JcAddExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Add, expr)

    override fun visitJcSubExpr(expr: JcSubExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Sub, expr)

    override fun visitJcMulExpr(expr: JcMulExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Mul, expr)

    override fun visitJcDivExpr(expr: JcDivExpr): UExpr<out USort>? =
        resolveDivisionOperator(JcBinaryOperator.Div, expr)

    override fun visitJcRemExpr(expr: JcRemExpr): UExpr<out USort>? =
        resolveDivisionOperator(JcBinaryOperator.Rem, expr)

    override fun visitJcShlExpr(expr: JcShlExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Shl, expr)

    override fun visitJcShrExpr(expr: JcShrExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Shr, expr)

    override fun visitJcUshrExpr(expr: JcUshrExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Ushr, expr)

    override fun visitJcOrExpr(expr: JcOrExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Or, expr)

    override fun visitJcAndExpr(expr: JcAndExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.And, expr)

    override fun visitJcXorExpr(expr: JcXorExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Xor, expr)

    override fun visitJcEqExpr(expr: JcEqExpr): UExpr<out USort>? = with(ctx) {
        if (expr.lhv.type is JcRefType) {
            resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort))
            }
        } else {
            resolveBinaryOperator(JcBinaryOperator.Eq, expr)
        }
    }

    override fun visitJcNeqExpr(expr: JcNeqExpr): UExpr<out USort>? = with(ctx) {
        if (expr.lhv.type is JcRefType) {
            resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort)).not()
            }
        } else {
            resolveBinaryOperator(JcBinaryOperator.Neq, expr)
        }
    }

    override fun visitJcGeExpr(expr: JcGeExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Ge, expr)

    override fun visitJcGtExpr(expr: JcGtExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Gt, expr)

    override fun visitJcLeExpr(expr: JcLeExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Le, expr)

    override fun visitJcLtExpr(expr: JcLtExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Lt, expr)

    override fun visitJcCmpExpr(expr: JcCmpExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmp, expr)

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmpg, expr)

    override fun visitJcCmplExpr(expr: JcCmplExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmpl, expr)

    override fun visitJcNegExpr(expr: JcNegExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.operand) { operand ->
            val wideOperand = if (operand.sort != operand.ctx.boolSort) {
                operand wideWith expr.operand.type
            } else {
                operand
            }
            JcUnaryOperator.Neg(wideOperand)
        }

    // endregion

    // region constants

    override fun visitJcBool(value: JcBool): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visitJcChar(value: JcChar): UExpr<out USort> = with(ctx) {
        mkBv(value.value.code, charSort)
    }

    override fun visitJcByte(value: JcByte): UExpr<out USort> = with(ctx) {
        mkBv(value.value, byteSort)
    }

    override fun visitJcShort(value: JcShort): UExpr<out USort> = with(ctx) {
        mkBv(value.value, shortSort)
    }

    override fun visitJcInt(value: JcInt): UExpr<out USort> = with(ctx) {
        mkBv(value.value, integerSort)
    }

    override fun visitJcLong(value: JcLong): UExpr<out USort> = with(ctx) {
        mkBv(value.value, longSort)
    }

    override fun visitJcFloat(value: JcFloat): UExpr<out USort> = with(ctx) {
        mkFp(value.value, floatSort)
    }

    override fun visitJcDouble(value: JcDouble): UExpr<out USort> = with(ctx) {
        mkFp(value.value, doubleSort)
    }

    override fun visitJcNullConstant(value: JcNullConstant): UExpr<out USort> = with(ctx) {
        nullRef
    }

    override fun visitJcStringConstant(value: JcStringConstant): UExpr<out USort> = with(ctx) {
        scope.calcOnState {
            // String.value type depends on the JVM version
            val charValues = when (stringValueField.fieldType.ifArrayGetElementType) {
                cp.char -> value.value.asSequence().map { mkBv(it.code, charSort) }
                cp.byte -> value.value.encodeToByteArray().asSequence().map { mkBv(it, byteSort) }
                else -> error("Unexpected string values type: ${stringValueField.fieldType}")
            }

            val valuesArrayDescriptor = arrayDescriptorOf(stringValueField.fieldType as JcArrayType)
            val elementType = requireNotNull(stringValueField.fieldType.ifArrayGetElementType)
            val charArrayRef = memory.malloc(valuesArrayDescriptor, typeToSort(elementType), charValues)

            // overwrite array type because descriptor is element type
            memory.types.allocate(charArrayRef.address, stringValueField.fieldType)

            // Equal string constants always have equal references
            val ref = mkStringConstRef(value.value, this)

            // String constants are immutable. Therefore, it is correct to overwrite value and type.
            val stringValueLValue = UFieldLValue(addressSort, ref, stringValueField.field)
            memory.write(stringValueLValue, charArrayRef)
            memory.types.allocate(ref.address, stringType)

            ref
        }
    }

    override fun visitJcMethodConstant(value: JcMethodConstant): UExpr<out USort> {
        TODO("Method constant")
    }

    override fun visitJcMethodType(value: JcMethodType): UExpr<out USort> {
        TODO("Method type")
    }

    private fun JcState.resolveClassRef(type: JcType): UConcreteHeapRef {
        val ref = mkTypeRef(type, this)

        // Ref type is java.lang.Class
        memory.types.allocate(ref.address, ctx.classType)

        // Save ref original class type
        val classRefType = memory.alloc(type)
        val classRefTypeLValue = UFieldLValue(ctx.addressSort, ref, ctx.classTypeSyntheticField)
        memory.write(classRefTypeLValue, classRefType)

        return ref
    }

    override fun visitJcClassConstant(value: JcClassConstant): UExpr<out USort> =
        scope.calcOnState {
            resolveClassRef(value.klass)
        }

    // endregion

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort>? = resolveCast(expr.operand, expr.type)

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.operand)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            val notEqualsNull = mkHeapRefEq(ref, memory.heap.nullRef()).not()
            val isExpr = memory.types.evalIsSubtype(ref, expr.targetType)
            mkAnd(notEqualsNull, isExpr)
        }
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.array)?.asExpr(addressSort) ?: return null
        checkNullPointer(ref) ?: return null
        val arrayDescriptor = arrayDescriptorOf(expr.array.type as JcArrayType)
        val lengthRef = UArrayLengthLValue(ref, arrayDescriptor)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }
        assertHardMaxArrayLength(length) ?: return null
        scope.assert(mkBvSignedLessOrEqualExpr(mkBv(0), length)) ?: return null
        length
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val size = resolveCast(expr.dimensions[0], ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        // TODO: other dimensions ( > 1)
        checkNewArrayLength(size) ?: return null
        val ref = scope.calcOnState { memory.malloc(expr.type, size) }
        ref
    }

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort> =
        scope.calcOnState { memory.alloc(expr.type) }

    override fun visitJcPhiExpr(expr: JcPhiExpr): UExpr<out USort> =
        error("Unexpected expr: $expr")

    // region invokes

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = expr.instance,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            with(invokeResolver) { resolveSpecialInvoke(expr.method.method, arguments) }
        }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = expr.instance,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            with(invokeResolver) {
                resolveVirtualInvoke(
                    method = expr.method.method,
                    arguments = arguments,
                    approximate = { state, method, args ->
                        approximateVirtualMethod(state, method, args)
                    }
                )
            }
        }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = null,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            with(invokeResolver) {
                approximateStaticMethod(expr.method.method, arguments) {
                    resolveStaticInvoke(expr.method.method, arguments)
                }
            }
        }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = null,
            argumentExprs = expr::args,
            argumentTypes = expr::callSiteArgTypes
        ) { arguments ->
            with(invokeResolver) {
                resolveDynamicInvoke(expr.method.method, arguments)
            }
        }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = null,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            with(invokeResolver) {
                resolveLambdaInvoke(expr.method.method, arguments)
            }
        }

    private inline fun resolveInvoke(
        method: JcTypedMethod,
        instanceExpr: JcValue?,
        argumentExprs: () -> List<JcValue>,
        argumentTypes: () -> List<JcType>,
        onNoCallPresent: JcStepScope.(List<UExpr<out USort>>) -> Unit,
    ): UExpr<out USort>? = ensureStaticFieldsInitialized(method.enclosingType) {
        val arguments = mutableListOf<UExpr<out USort>>()

        if (instanceExpr != null) {
            val instance = resolveJcExpr(instanceExpr)?.asExpr(ctx.addressSort) ?: return null
            checkNullPointer(instance) ?: return null

            // Ensure instance is subtype of method class
            if (!assertIsSubtype(instance, method.enclosingType)) return null

            arguments += instance
        }

        argumentExprs().zip(argumentTypes()) { expr, type ->
            arguments += resolveJcExpr(expr, type) ?: return null
        }

        resolveInvokeNoStaticInitializationCheck { onNoCallPresent(arguments) }
    }

    private inline fun resolveInvokeNoStaticInitializationCheck(
        onNoCallPresent: JcStepScope.() -> Unit,
    ): UExpr<out USort>? {
        val result = scope.calcOnState { methodResult }
        return when (result) {
            is JcMethodResult.Success -> {
                scope.doWithState { methodResult = JcMethodResult.NoCall }
                result.value
            }

            is JcMethodResult.NoCall -> {
                scope.onNoCallPresent()
                null
            }

            is JcMethodResult.JcException -> error("Exception should be handled earlier")
        }
    }

    // endregion

    // region jc locals

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcThis(value: JcThis): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcArgument(value: JcArgument): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    // endregion

    // region jc complex values

    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort>? {
        val lValue = resolveFieldRef(value.instance, value.field) ?: return null
        val expr = scope.calcOnState { memory.read(lValue) }

        if (assertIsSubtype(expr, value.type)) return expr

        return null
    }

    override fun visitJcArrayAccess(value: JcArrayAccess): UExpr<out USort>? {
        val lValue = resolveArrayAccess(value.array, value.index) ?: return null
        val expr = scope.calcOnState { memory.read(lValue) }

        if (assertIsSubtype(expr, value.type)) return expr

        return null
    }

    private fun assertIsSubtype(expr: KExpr<out USort>, type: JcType): Boolean {
        if (type is JcRefType) {
            val heapRef = expr.asExpr(ctx.addressSort)
            val isExpr = scope.calcOnState { memory.types.evalIsSubtype(heapRef, type) }
            scope.assert(isExpr) ?: return false
        }

        return true
    }

    // endregion

    // region lvalue resolving

    private fun resolveFieldRef(instance: JcValue?, field: JcTypedField): ULValue? =
        ensureStaticFieldsInitialized(field.enclosingType) {
            with(ctx) {
                if (instance != null) {
                    val instanceRef = resolveJcExpr(instance)?.asExpr(addressSort) ?: return null
                    checkNullPointer(instanceRef) ?: return null

                    // Ensure instance is subtype of field class
                    if (!assertIsSubtype(instanceRef, field.enclosingType)) return null

                    val sort = ctx.typeToSort(field.fieldType)
                    UFieldLValue(sort, instanceRef, field.field)
                } else {
                    val sort = ctx.typeToSort(field.fieldType)
                    val classRef = scope.calcOnState {
                        resolveClassRef(field.enclosingType)
                    }
                    UFieldLValue(sort, classRef, field.field)
                }
            }
        }

    /**
     * Run a class static initializer for [type] if it didn't run before the current state.
     * The class static initialization state is tracked by the synthetic [staticFieldsInitializedFlagField] field.
     * */
    private inline fun <T> ensureStaticFieldsInitialized(type: JcRefType, body: () -> T): T? {
        // java.lang.Object has no static fields, but has non-trivial initializer
        if (type == ctx.cp.objectType) {
            return body()
        }

        val initializer = type.jcClass.declaredMethods.firstOrNull { it.isClassInitializer }

        // Class has no static initializer
        if (initializer == null) {
            return body()
        }

        val classRef = scope.calcOnState { resolveClassRef(type) }

        val initializedFlag = staticFieldsInitializedFlag(type, classRef)

        val staticFieldsInitialized = scope.calcOnState {
            memory.read(initializedFlag).asExpr(ctx.booleanSort)
        }


        if (staticFieldsInitialized.isTrue) {
            scope.doWithState {
                // Handle static initializer result
                val result = methodResult
                if (result is JcMethodResult.Success && result.method == initializer) {
                    methodResult = JcMethodResult.NoCall
                }
            }

            return body()
        }

        // Run static initializer before the current statement
        scope.doWithState {
            memory.write(initializedFlag, ctx.trueExpr)
        }
        with(invokeResolver) {
            approximateStaticMethod(initializer, emptyList()) {
                scope.resolveStaticInvoke(initializer, emptyList())
            }
        }
        return null
    }

    private fun staticFieldsInitializedFlag(type: JcRefType, classRef: UHeapRef) =
        UFieldLValue(
            fieldSort = ctx.booleanSort,
            field = JcFieldImpl(type.jcClass, staticFieldsInitializedFlagField),
            ref = classRef
        )

    private fun resolveArrayAccess(array: JcValue, index: JcValue): UArrayIndexLValue<JcType>? = with(ctx) {
        val arrayRef = resolveJcExpr(array)?.asExpr(addressSort) ?: return null
        checkNullPointer(arrayRef) ?: return null

        val arrayDescriptor = arrayDescriptorOf(array.type as JcArrayType)

        val idx = resolveCast(index, ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        val lengthRef = UArrayLengthLValue(arrayRef, arrayDescriptor)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }

        assertHardMaxArrayLength(length) ?: return null

        checkArrayIndex(idx, length) ?: return null

        val elementType = requireNotNull(array.type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        return UArrayIndexLValue(cellSort, arrayRef, idx, arrayDescriptor)
    }

    private fun resolveLocal(local: JcLocal): URegisterLValue {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterLValue(sort, localIdx)
    }

    // endregion

    // region implicit exceptions

    private fun allocateException(type: JcRefType): (JcState) -> Unit = { state ->
        val address = state.memory.alloc(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun checkArrayIndex(idx: USizeExpr, length: USizeExpr) = with(ctx) {
        val inside = (mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and (mkBvSignedLessExpr(idx, length))

        scope.fork(
            inside,
            blockOnFalseState = allocateException(arrayIndexOutOfBoundsExceptionType)
        )
    }

    private fun checkNewArrayLength(length: UExpr<USizeSort>) = with(ctx) {
        assertHardMaxArrayLength(length) ?: return null

        val lengthIsNonNegative = mkBvSignedLessOrEqualExpr(mkBv(0), length)

        scope.fork(
            lengthIsNonNegative,
            blockOnFalseState = allocateException(negativeArraySizeExceptionType)
        )
    }

    private fun checkDivisionByZero(expr: UExpr<out USort>) = with(ctx) {
        val sort = expr.sort
        if (sort !is UBvSort) {
            return Unit
        }
        val neqZero = mkEq(expr.cast(), mkBv(0, sort)).not()
        scope.fork(
            neqZero,
            blockOnFalseState = allocateException(arithmeticExceptionType)
        )
    }

    private fun checkNullPointer(ref: UHeapRef) = with(ctx) {
        val neqNull = mkHeapRefEq(ref, nullRef).not()
        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(nullPointerExceptionType)
        )
    }

    // endregion

    // region hard assertions

    private fun assertHardMaxArrayLength(length: USizeExpr): Unit? = with(ctx) {
        val lengthLeThanMaxLength = mkBvSignedLessOrEqualExpr(length, mkBv(hardMaxArrayLength))
        scope.assert(lengthLeThanMaxLength)
    }

    // endregion

    private fun resolveBinaryOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        // we don't want to have extra casts on booleans
        if (lhs.sort == ctx.boolSort && rhs.sort == ctx.boolSort) {
            resolvePrimitiveCast(operator(lhs, rhs), ctx.cp.boolean, expr.type as JcPrimitiveType)
        } else {
            operator(lhs wideWith expr.lhv.type, rhs wideWith expr.rhv.type)
        }
    }

    private fun resolveShiftOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        val wideLhs = lhs wideWith expr.lhv.type
        val preWideRhs = rhs wideWith expr.rhv.type

        val lhsSize = (wideLhs.sort as UBvSort).sizeBits
        val rhsSize = (preWideRhs.sort as UBvSort).sizeBits

        val wideRhs = if (lhsSize == rhsSize) {
            preWideRhs
        } else {
            check(lhsSize == Long.SIZE_BITS.toUInt() && rhsSize == Int.SIZE_BITS.toUInt()) {
                "Unexpected shift arguments: $lhs, $rhs"
            }
            // Wide rhs up to 64 bits to match lhs sort
            preWideRhs.ensureBvExpr().mkNarrow(Long.SIZE_BITS, signed = true)
        }

        operator(wideLhs, wideRhs)
    }

    private fun resolveDivisionOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        checkDivisionByZero(rhs) ?: return null
        operator(lhs wideWith expr.lhv.type, rhs wideWith expr.rhv.type)
    }

    private fun resolveCast(
        operand: JcExpr,
        type: JcType,
    ) = resolveAfterResolved(operand) { expr ->
        check(operand.type != ctx.cp.void) { "Unexpected expr of type void: $operand" }
        when (type) {
            is JcRefType -> resolveReferenceCast(expr.asExpr(ctx.addressSort), operand.type as JcRefType, type)
            is JcPrimitiveType -> resolvePrimitiveCast(expr, operand.type as JcPrimitiveType, type)
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveReferenceCast(
        expr: UHeapRef,
        typeBefore: JcRefType,
        type: JcRefType,
    ): UHeapRef? {
        return if (!typeBefore.isAssignable(type)) {
            val isExpr = scope.calcOnState { memory.types.evalIsSubtype(expr, type) }
            scope.fork(
                isExpr,
                blockOnFalseState = allocateException(classCastExceptionType)
            ) ?: return null
            expr
        } else {
            expr
        }
    }

    private fun resolvePrimitiveCast(
        expr: UExpr<out USort>,
        typeBefore: JcPrimitiveType,
        type: JcPrimitiveType,
    ): UExpr<out USort> {
        // we need this, because char is unsigned, so it should be widened before a cast
        val wideExpr = if (typeBefore == ctx.cp.char) {
            expr wideWith typeBefore
        } else {
            expr
        }

        return when (type) {
            ctx.cp.boolean -> JcUnaryOperator.CastToBoolean(wideExpr)
            ctx.cp.short -> JcUnaryOperator.CastToShort(wideExpr)
            ctx.cp.int -> JcUnaryOperator.CastToInt(wideExpr)
            ctx.cp.long -> JcUnaryOperator.CastToLong(wideExpr)
            ctx.cp.float -> JcUnaryOperator.CastToFloat(wideExpr)
            ctx.cp.double -> JcUnaryOperator.CastToDouble(wideExpr)
            ctx.cp.byte -> JcUnaryOperator.CastToByte(wideExpr)
            ctx.cp.char -> JcUnaryOperator.CastToChar(wideExpr)
            else -> error("Unexpected cast expression: ($type) $expr of $typeBefore")
        }
    }

    private infix fun UExpr<out USort>.wideWith(
        type: JcType,
    ): UExpr<out USort> {
        require(type is JcPrimitiveType)
        return wideTo32BitsIfNeeded(type.isSigned)
    }

    private val JcPrimitiveType.isSigned
        get() = this != classpath.char

    private fun <T> resolveAfterResolved(
        dependency: JcExpr,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolveJcExpr(dependency) ?: return null
        return block(result)
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: JcExpr,
        dependency1: JcExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolveJcExpr(dependency0) ?: return null
        val result1 = resolveJcExpr(dependency1) ?: return null
        return block(result0, result1)
    }

    private val arrayIndexOutOfBoundsExceptionType by lazy {
        ctx.extractJcRefType(IndexOutOfBoundsException::class)
    }

    private val negativeArraySizeExceptionType by lazy {
        ctx.extractJcRefType(NegativeArraySizeException::class)
    }

    private val arithmeticExceptionType by lazy {
        ctx.extractJcRefType(ArithmeticException::class)
    }

    private val nullPointerExceptionType by lazy {
        ctx.extractJcRefType(NullPointerException::class)
    }

    private val classCastExceptionType by lazy {
        ctx.extractJcRefType(ClassCastException::class)
    }

    private val arrayStoreExceptionType by lazy {
        ctx.extractJcRefType(ArrayStoreException::class)
    }

    private fun approximateVirtualMethod(
        state: JcState,
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): UExpr<out USort>? {
        if (method.enclosingClass == ctx.cp.objectClass) {
            state.approximateObjectVirtualMethod(method, arguments)?.let { return it }
        }

        if (method.enclosingClass == ctx.classType.jcClass) {
            approximateClassVirtualMethod(method)?.let { return it }
        }

        if (method.enclosingClass.name == "jdk.internal.misc.Unsafe") {
            state.approximateUnsafeVirtualMethod(method, arguments)?.let { return it }
        }

        return approximateEmptyNativeMethod(method)
    }

    private inline fun approximateStaticMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
        noApproximation: () -> Unit
    ) {
        if (method.enclosingClass == ctx.classType.jcClass) {
            if (approximateClassStaticMethod(method, arguments)) return
        }

        if (method.enclosingClass.name == "java.lang.System") {
            if (approximateSystemStaticMethod(method, arguments)) return
        }

        if (method.enclosingClass.name == "java.lang.StringUTF16") {
            if (approximateStringUtf16StaticMethod(method)) return
        }

        if (method.enclosingClass.name == "java.lang.Float") {
            if (approximateFloatStaticMethod(method, arguments)) return
        }

        if (method.enclosingClass.name == "java.lang.Double") {
            if (approximateDoubleStaticMethod(method, arguments)) return
        }

        val emptyNativeApprox = approximateEmptyNativeMethod(method)
        if (emptyNativeApprox != null) {
            scope.doWithState {
                exitWithValue(method, emptyNativeApprox)
            }
            return
        }

        return noApproximation()
    }

    private fun approximateEmptyNativeMethod(method: JcMethod): UExpr<out USort>? {
        if (method.isNative && method.hasVoidReturnType() && method.parameters.isEmpty()) {
            if (method.enclosingClass.declaration.location.isRuntime) {
                /**
                 * Native methods in the standard library with no return value and no
                 * arguments have no visible effect and can be skipped
                 * */
                return ctx.voidValue
            }
        }

        return null
    }

    private fun approximateClassStaticMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): Boolean {
        /**
         * Approximate retrieval of class instance for primitives.
         * */
        if (method.name == "getPrimitiveClass") {
            val classNameRef = arguments.single()
            val predefinedTypeNames = scope.calcOnState {
                ctx.primitiveTypes.associateBy {
                    mkStringConstRef(it.typeName, this)
                }
            }

            val primitive = predefinedTypeNames[classNameRef] ?: return false

            scope.doWithState {
                val classRef = resolveClassRef(primitive)
                exitWithValue(method, classRef)
            }
            return true
        }

        return false
    }

    private fun approximateClassVirtualMethod(
        method: JcMethod
    ): UExpr<out USort>? {
        /**
         * Approximate assertions enabled check.
         * It is correct to enable assertions during analysis.
         * */
        if (method.name == "desiredAssertionStatus") {
            return ctx.trueExpr
        }

        return null
    }

    private fun JcState.approximateObjectVirtualMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): UExpr<out USort>? {
        if (method.name == "getClass") {
            val instance = arguments.first().asExpr(ctx.addressSort)
            val possibleTypes = memory.typeStreamOf(instance).take(2)

            /**
             * Since getClass is a virtual method, typeStream has been constrained
             * to a single concrete type by the [JcInvokeResolver.resolveVirtualInvoke]
             * */
            val type = possibleTypes.singleOrNull() ?: return null
            return resolveClassRef(type)
        }

        return null
    }

    private fun JcState.approximateUnsafeVirtualMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): UExpr<out USort>? {
        // Array offset is usually the same on various JVM
        if (method.name == "arrayBaseOffset0") {
            return ctx.mkBv(16, ctx.integerSort)
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
                resolveClassRef(ctx.cp.arrayTypeOf(type))
            }

            val arrayTypeRef = arguments.last().asExpr(ctx.addressSort)

            return primitiveArrayRefScale.entries.fold(
                // All non-primitive (object) arrays usually have 4 bytes scale on various JVM
                ctx.mkBv(4, ctx.integerSort) as UExpr<UBv32Sort>
            ) { res, (typeRef, scale) ->
                ctx.mkIte(ctx.mkHeapRefEq(arrayTypeRef, typeRef), ctx.mkBv(scale, ctx.integerSort), res)
            }
        }

        return null
    }

    private fun approximateSystemStaticMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): Boolean {
        if (method.name == "arraycopy") {
            // Object src, int srcPos, Object dest, int destPos, int length
            val (srcRef, srcPos, dstRef, dstPos, length) = arguments

            checkNullPointer(srcRef.asExpr(ctx.addressSort)) ?: return true
            checkNullPointer(dstRef.asExpr(ctx.addressSort)) ?: return true

            val possibleElementTypes = ctx.primitiveTypes + ctx.cp.objectType
            val possibleArrayTypes = possibleElementTypes.map { ctx.cp.arrayTypeOf(it) }

            val arrayTypeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()
            possibleArrayTypes.forEach { type ->
                addArrayCopyForType(
                    method, arrayTypeConstraintsWithBlockOnStates, type,
                    srcRef.asExpr(ctx.addressSort), srcPos.asExpr(ctx.sizeSort),
                    dstRef.asExpr(ctx.addressSort), dstPos.asExpr(ctx.sizeSort),
                    length.asExpr(ctx.sizeSort)
                )
            }

            val arrayTypeConstraints = possibleArrayTypes.map { type ->
                scope.calcOnState {
                    ctx.mkAnd(
                        memory.types.evalIsSubtype(srcRef.asExpr(ctx.addressSort), type),
                        memory.types.evalIsSubtype(dstRef.asExpr(ctx.addressSort), type)
                    )
                }
            }
            val unknownArrayType = ctx.mkAnd(arrayTypeConstraints.map { ctx.mkNot(it) })
            arrayTypeConstraintsWithBlockOnStates += unknownArrayType to allocateException(arrayStoreExceptionType)

            scope.forkMulti(arrayTypeConstraintsWithBlockOnStates)
            return true
        }

        return false
    }

    private fun addArrayCopyForType(
        method: JcMethod,
        branches: MutableList<Pair<UBoolExpr, (JcState) -> Unit>>,
        type: JcArrayType,
        srcRef: UHeapRef,
        srcPos: USizeExpr,
        dstRef: UHeapRef,
        dstPos: USizeExpr,
        length: USizeExpr
    ) = with(ctx) {
        val arrayDescriptor = arrayDescriptorOf(type)
        val elementType = requireNotNull(type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        val arrayTypeConstraint = scope.calcOnState {
            mkAnd(
                memory.types.evalIsSubtype(srcRef.asExpr(addressSort), type),
                memory.types.evalIsSubtype(dstRef.asExpr(addressSort), type)
            )
        }

        val srcLengthRef = UArrayLengthLValue(srcRef, arrayDescriptor)
        val srcLength = scope.calcOnState { memory.read(srcLengthRef).asExpr(sizeSort) }

        val dstLengthRef = UArrayLengthLValue(dstRef, arrayDescriptor)
        val dstLength = scope.calcOnState { memory.read(dstLengthRef).asExpr(sizeSort) }

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
                srcRef.asExpr(ctx.addressSort),
                dstRef.asExpr(ctx.addressSort),
                arrayDescriptor,
                cellSort,
                srcPos.asExpr(ctx.sizeSort),
                dstPos.asExpr(ctx.sizeSort),
                length.asExpr(ctx.sizeSort)
            )

            state.exitWithValue(method, ctx.voidValue)
        }

        branches += arrayCopySuccessConstraint to arrayCopyBlock
    }

    private fun approximateStringUtf16StaticMethod(method: JcMethod): Boolean {
        // Use common property value as approximation
        if (method.name == "isBigEndian") {
            scope.doWithState {
                exitWithValue(method, ctx.falseExpr)
            }
            return true
        }
        return false
    }

    private fun approximateFloatStaticMethod(method: JcMethod, arguments: List<UExpr<out USort>>): Boolean {
        if (method.name == "floatToRawIntBits") {
            val value = arguments.single().asExpr(ctx.floatSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.integerSort)
            scope.doWithState {
                exitWithValue(method, result)
            }
            return true
        }

        if (method.name == "intBitsToFloat") {
            val value = arguments.single().asExpr(ctx.integerSort)
            val result = mkFpFromBits(ctx.floatSort, value)
            scope.doWithState {
                exitWithValue(method, result)
            }
            return true
        }

        return false
    }

    private fun approximateDoubleStaticMethod(method: JcMethod, arguments: List<UExpr<out USort>>): Boolean {
        if (method.name == "doubleToRawLongBits") {
            val value = arguments.single().asExpr(ctx.doubleSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.longSort)
            scope.doWithState {
                exitWithValue(method, result)
            }
            return true
        }

        if (method.name == "longBitsToDouble") {
            val value = arguments.single().asExpr(ctx.longSort)
            val result = mkFpFromBits(ctx.doubleSort, value)
            scope.doWithState {
                exitWithValue(method, result)
            }
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

    companion object {
        /**
         * Synthetic field to track static field initialization state.
         * */
        private val staticFieldsInitializedFlagField by lazy {
            FieldInfo(
                name = "__initialized__",
                signature = null,
                access = 0,
                type = PredefinedPrimitives.Boolean,
                annotations = emptyList()
            )
        }
    }
}
