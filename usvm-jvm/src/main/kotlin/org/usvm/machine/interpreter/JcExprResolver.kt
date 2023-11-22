package org.usvm.machine.interpreter

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import io.ksmt.utils.uncheckedCast
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeVariable
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
import org.jacodb.api.cfg.JcSimpleValue
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
import org.jacodb.api.ext.enumValues
import org.jacodb.api.ext.float
import org.jacodb.api.ext.ifArrayGetElementType
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.FieldInfo
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.api.allocateArrayInitialized
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import org.usvm.machine.operator.JcBinaryOperator
import org.usvm.machine.operator.JcUnaryOperator
import org.usvm.machine.operator.ensureBvExpr
import org.usvm.machine.operator.mkNarrow
import org.usvm.machine.operator.wideTo32BitsIfNeeded
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addConcreteMethodCallStmt
import org.usvm.machine.state.addDynamicCall
import org.usvm.machine.state.addVirtualMethodCallStmt
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.util.allocHeapRef
import org.usvm.util.enumValuesField
import org.usvm.util.write
import org.usvm.utils.logAssertFailure

/**
 * An expression resolver based on JacoDb 3-address code. A result of resolving is `null`, iff
 * the original state is dead, as stated in [JcStepScope].
 */
class JcExprResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    localToIdx: (JcMethod, JcLocal) -> Int,
    mkTypeRef: (JcType) -> UConcreteHeapRef,
    mkStringConstRef: (String) -> UConcreteHeapRef,
    private val classInitializerAnalysisAlwaysRequiredForType: (JcRefType) -> Boolean,
    private val hardMaxArrayLength: Int = 1_500, // TODO: move to options
) : JcExprVisitor<UExpr<out USort>?> {
    val simpleValueResolver: JcSimpleValueResolver = JcSimpleValueResolver(
        ctx,
        scope,
        localToIdx,
        mkTypeRef,
        mkStringConstRef
    )

    /**
     * Resolves the [expr] and casts it to match the desired [type].
     *
     * @return a symbolic expression, with the sort corresponding to the [type].
     */
    fun resolveJcExpr(expr: JcExpr, type: JcType = expr.type): UExpr<out USort>? {
        val resolvedExpr = if (expr.type != type && type is JcPrimitiveType) {
            // Only primitive casts may appear here because reference casts are handled with cast instruction
            resolvePrimitiveCast(expr, type)
        } else {
            expr.accept(this)
        } ?: return null

        ensureExprCorrectness(resolvedExpr, type) ?: return null

        return resolvedExpr
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
    fun resolveLValue(value: JcValue): ULValue<*, *>? =
        when (value) {
            is JcFieldRef -> resolveFieldRef(value.instance, value.field)
            is JcArrayAccess -> resolveArrayAccess(value.array, value.index)
            is JcLocal -> simpleValueResolver.resolveLocal(value)
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

    override fun visitJcBool(value: JcBool): UExpr<out USort> = simpleValueResolver.visitJcBool(value)

    override fun visitJcChar(value: JcChar): UExpr<out USort> = simpleValueResolver.visitJcChar(value)

    override fun visitJcByte(value: JcByte): UExpr<out USort> = simpleValueResolver.visitJcByte(value)

    override fun visitJcShort(value: JcShort): UExpr<out USort> = simpleValueResolver.visitJcShort(value)

    override fun visitJcInt(value: JcInt): UExpr<out USort> = simpleValueResolver.visitJcInt(value)

    override fun visitJcLong(value: JcLong): UExpr<out USort> = simpleValueResolver.visitJcLong(value)

    override fun visitJcFloat(value: JcFloat): UExpr<out USort> = simpleValueResolver.visitJcFloat(value)

    override fun visitJcDouble(value: JcDouble): UExpr<out USort> = simpleValueResolver.visitJcDouble(value)

    override fun visitJcNullConstant(
        value: JcNullConstant,
    ): UExpr<out USort> = simpleValueResolver.visitJcNullConstant(value)

    override fun visitJcStringConstant(
        value: JcStringConstant,
    ): UExpr<out USort> = simpleValueResolver.visitJcStringConstant(value)

    override fun visitJcMethodConstant(
        value: JcMethodConstant,
    ): UExpr<out USort> = simpleValueResolver.visitJcMethodConstant(value)

    override fun visitJcMethodType(
        value: JcMethodType,
    ): UExpr<out USort> = simpleValueResolver.visitJcMethodType(value)

    override fun visitJcClassConstant(
        value: JcClassConstant,
    ): UExpr<out USort> = simpleValueResolver.visitJcClassConstant(value)
    // endregion

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort>? =
        // Note that primitive types may appear in JcCastExpr
        resolveCast(expr.operand, expr.type)

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.operand)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            val notEqualsNull = mkHeapRefEq(ref, memory.nullRef()).not()
            val isExpr = memory.types.evalIsSubtype(ref, expr.targetType)
            mkAnd(notEqualsNull, isExpr)
        }
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.array)?.asExpr(addressSort) ?: return null
        checkNullPointer(ref) ?: return null
        val arrayDescriptor = arrayDescriptorOf(expr.array.type as JcArrayType)
        val lengthRef = UArrayLengthLValue(ref, arrayDescriptor, sizeSort)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }
        assertHardMaxArrayLength(length) ?: return null

        scope.assert(mkBvSignedLessOrEqualExpr(mkBv(0), length))
            .logAssertFailure { "JcExprResolver: array length >= 0" }
            ?: return null

        length
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val size = resolvePrimitiveCast(expr.dimensions[0], ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        // TODO: other dimensions ( > 1)
        checkNewArrayLength(size) ?: return null

        scope.calcOnState {
            val ref = memory.allocHeapRef(expr.type, useStaticAddress = useStaticAddressForAllocation())

            val arrayDescriptor = arrayDescriptorOf(expr.type as JcArrayType)
            memory.write(UArrayLengthLValue(ref, arrayDescriptor, sizeSort), size)
            // overwrite array type because descriptor is element type (which is Object for arrays of refs)
            memory.types.allocate(ref.address, expr.type)

            ref
        }
    }

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort> =
        scope.calcOnState { memory.allocHeapRef(expr.type, useStaticAddress = useStaticAddressForAllocation()) }

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
            scope.doWithState { addConcreteMethodCallStmt(expr.method.method, arguments) }
        }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = expr.instance,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            scope.doWithState { addVirtualMethodCallStmt(expr.method.method, arguments) }
        }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = null,
            argumentExprs = expr::args,
            argumentTypes = { expr.method.parameters.map { it.type } }
        ) { arguments ->
            scope.doWithState { addConcreteMethodCallStmt(expr.method.method, arguments) }
        }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort>? =
        resolveInvoke(
            expr.method,
            instanceExpr = null,
            argumentExprs = { expr.callSiteArgs },
            argumentTypes = { expr.callSiteArgTypes }
        ) { callSiteArguments ->
            scope.doWithState { addDynamicCall(expr, callSiteArguments) }
        }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort>? {
        val callSiteArgs = expr.callSiteArgs.zip(expr.callSiteArgTypes) { arg, type ->
            resolveJcExpr(arg, type) ?: return null
        }

        val callSiteRef = scope.calcOnState { memory.allocConcrete(expr.callSiteReturnType) }
        val callSite = JcLambdaCallSite(callSiteRef, expr, callSiteArgs)
        scope.doWithState { memory.writeCallSite(callSite) }

        return callSiteRef
    }

    private fun UWritableMemory<JcType>.writeCallSite(callSite: JcLambdaCallSite) {
        val callSiteRegion = getRegion(ctx.lambdaCallSiteRegionId) as JcLambdaCallSiteMemoryRegion
        val updatedRegion = callSiteRegion.writeCallSite(callSite)
        setRegion(ctx.lambdaCallSiteRegionId, updatedRegion)
    }

    private inline fun resolveInvoke(
        method: JcTypedMethod,
        instanceExpr: JcValue?,
        argumentExprs: () -> List<JcValue>,
        argumentTypes: () -> List<JcType>,
        onNoCallPresent: JcStepScope.(List<UExpr<out USort>>) -> Unit,
    ): UExpr<out USort>? {
        val instanceRef = if (instanceExpr != null) {
            resolveJcExpr(instanceExpr)?.asExpr(ctx.addressSort) ?: return null
        } else {
            null
        }

        val arguments = mutableListOf<UExpr<out USort>>()
        if (instanceRef != null) {
            checkNullPointer(instanceRef) ?: return null

            // Ensure instance is subtype of method class
            if (!assertIsSubtype(instanceRef, method.enclosingType)) return null

            arguments += instanceRef
        }

        argumentExprs().zip(argumentTypes()) { expr, type ->
            arguments += resolveJcExpr(expr, type) ?: return null
        }

        return resolveInvokeNoStaticInitializationCheck { onNoCallPresent(arguments) }
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

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort> = simpleValueResolver.visitJcLocalVar(value)

    override fun visitJcThis(value: JcThis): UExpr<out USort> = simpleValueResolver.visitJcThis(value)

    override fun visitJcArgument(value: JcArgument): UExpr<out USort> = simpleValueResolver.visitJcArgument(value)

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
            scope.assert(isExpr)
                .logAssertFailure { "JcExprResolver: subtype constraint ${type.typeName}" }
                ?: return false
        }

        return true
    }

    // endregion

    // region lvalue resolving

    private fun resolveFieldRef(instance: JcValue?, field: JcTypedField): ULValue<*, *>? {
        with(ctx) {
            val instanceRef = if (instance != null) {
                resolveJcExpr(instance)?.asExpr(addressSort) ?: return null
            } else {
                null
            }

            if (instanceRef != null) {
                checkNullPointer(instanceRef) ?: return null

                // Ensure instance is subtype of field class
                if (!assertIsSubtype(instanceRef, field.enclosingType)) return null

                val sort = ctx.typeToSort(field.fieldType)
                return UFieldLValue(sort, instanceRef, field.field)
            }

            return ensureStaticFieldsInitialized(field.enclosingType, classInitializerAnalysisRequired = true) {
                val sort = ctx.typeToSort(field.fieldType)
                JcStaticFieldLValue(field.field, sort)
            }
        }
    }

    fun ensureExprCorrectness(expr: UExpr<*>, type: JcType): Unit? {
        if (type !is JcClassType || !type.jcClass.isEnum) {
            return Unit
        }

        return ensureStaticFieldsInitialized(type.jcClass.toType(), classInitializerAnalysisRequired = true) {
            scope.calcOnState {
                ensureEnumInstanceCorrectness(expr.asExpr(ctx.addressSort), type.jcClass)
            }
        }
    }

    /**
     * This method adds a few constraints for the instance of an enum type to satisfy its invariants in Java:
     * - Its ordinal takes this semi-interval: [0..$VALUES.size).
     * - It equals by reference to the one of the enum constant of this enum type or null — this invariant is represented
     * as a constraint that this instance equals by reference to the array reading from the $VALUES field by its ordinal or null.
     *
     * Without such constraints, false positive can appear — for example, forking on the negative ordinal, or incorrect enum
     * values could be constructed as method parameters.
     */
    private fun JcState.ensureEnumInstanceCorrectness(
        enumInstance: UHeapRef,
        type: JcClassOrInterface,
    ): Unit? = with(ctx) {
        if (enumInstance is UConcreteHeapRef) {
            // We do not need to ensure correctness for existing enum constants
            return Unit
        }

        // For null enum values, we do not need any correctness constraints
        if (enumInstance is UNullRef) {
            return Unit
        }

        val enumValues = type.enumValues

        val maxOrdinalValue = enumValues!!.size.toBv()
        val ordinalFieldOfEnumInstanceLValue = UFieldLValue(sizeSort, enumInstance, enumOrdinalField)
        val ordinalFieldValue = memory.read(ordinalFieldOfEnumInstanceLValue)

        val ordinalCorrectnessConstraints =
            mkBvSignedLessOrEqualExpr(mkBv(0), ordinalFieldValue) and mkBvSignedLessExpr(
                ordinalFieldValue,
                maxOrdinalValue
            )

        val enumValuesField = type.enumValuesField
        val enumValuesFieldLValue = JcStaticFieldLValue(enumValuesField.field, addressSort)
        val enumValuesRef = memory.read(enumValuesFieldLValue)

        val oneOfEnumInstancesLValue =
            UArrayIndexLValue(addressSort, enumValuesRef, ordinalFieldValue, cp.objectType)
        val oneOfEnumInstancesRef = memory.read(oneOfEnumInstancesLValue)
        val oneOfEnumInstancesEqualityConstraint = mkHeapRefEq(enumInstance, oneOfEnumInstancesRef)

        val oneOfEnumInstances = ordinalCorrectnessConstraints and oneOfEnumInstancesEqualityConstraint
        val isEnumNull = mkHeapRefEq(enumInstance, nullRef)
        // A dirty hack to make both branches possible - with null enum value and
        // the enum value equal to the one of corresponding enum constants
        val invariantsConstraint = mkIteNoSimplify(isEnumNull, trueExpr, oneOfEnumInstances)


        scope.assert(invariantsConstraint)
            .logAssertFailure { "JcExprResolver: enum correctness constraint" }
    }

    /**
     * This method ensures enum constants are correct in any model. It is provided by adding a few constraints:
     * - The length of the $VALUES field is always the same as after interpreting the corresponding static initializer.
     * - The ordinal of each enum constant is always the same as after interpreting the corresponding static initializer.
     * - Each enum constant always equals by reference to the array reading from the $VALUES array by its ordinal.
     *
     * Without such constraints, incorrect enum values could be constructed in case of aliasing method parameters with
     * the enum constants (represented as static refs), or the $VALUES array.
     */
    private fun JcState.ensureEnumStaticInitializerInvariants(type: JcClassOrInterface) = with(ctx) {
        val enumValues = type.enumValues ?: error("Expected enum values containing in the enum type $type")
        val enumValuesField = type.enumValuesField
        val enumValuesFieldLValue = JcStaticFieldLValue(enumValuesField.field, addressSort)
        val enumValuesRef = memory.read(enumValuesFieldLValue)

        val enumValuesType = enumValuesField.fieldType as JcArrayType
        val enumValuesArrayDescriptor = arrayDescriptorOf(enumValuesType)

        val enumValuesFieldLengthLValue = UArrayLengthLValue(enumValuesRef, enumValuesArrayDescriptor, sizeSort)
        val enumValuesFieldLengthBeforeClinit =
            enumValuesFieldLengthLValue.memoryRegionId.emptyRegion().read(enumValuesFieldLengthLValue)
        val enumValuesFieldLengthAfterClinit = memory.read(enumValuesFieldLengthLValue)

        // Ensure that $VALUES in a model has the same length as the $VALUES in the memory
        scope.assert(mkEq(enumValuesFieldLengthBeforeClinit, enumValuesFieldLengthAfterClinit))
            ?: error("Cannot assert correctness constraint for the \$VALUES of the enum class ${type.name}")

        enumValues.indices.forEach { ordinal ->
            // {0x1 <- 1}{0x2 <- 2}ordinal
            // read(ordinal, 0x1) = 1
            // read(ordinal, 0x2) = 2
            val enumConstantLValue =
                UArrayIndexLValue(addressSort, enumValuesRef, mkSizeExpr(ordinal), cp.objectType)
            val enumConstantRefAfterClinit = memory.read(enumConstantLValue)
            val enumConstantRefBeforeClinit =
                enumConstantLValue.memoryRegionId.emptyRegion().read(enumConstantLValue)

            val ordinalFieldLValue =
                UFieldLValue(sizeSort, enumConstantRefAfterClinit, enumOrdinalField)
            val ordinalFieldValueAfterClinit = memory.read(ordinalFieldLValue)
            val ordinalEmptyRegion = ordinalFieldLValue.memoryRegionId.emptyRegion()
            val ordinalFieldValueBeforeClinit = ordinalEmptyRegion.read(ordinalFieldLValue)

            // Ensure that the ordinal of each enum constant equals to the real ordinal value
            scope.assert(mkEq(ordinalFieldValueAfterClinit, ordinalFieldValueBeforeClinit))
                ?: error("Cannot assert enum correctness constraint for a constant of the enum class ${type.name}")

            // Ensure that each enum constant in a model equals by reference to the corresponding value in the memory
            scope.assert(mkEq(enumConstantRefBeforeClinit, enumConstantRefAfterClinit))
                ?: error("Cannot assert enum correctness constraint for a constant of the enum class ${type.name}")
        }
    }


    /**
     * Run a class static initializer for [type] if it didn't run before the current state.
     * The class static initialization state is tracked by the synthetic [staticFieldsInitializedFlagField] field.
     * */
    private inline fun <T> ensureStaticFieldsInitialized(
        type: JcRefType,
        classInitializerAnalysisRequired: Boolean,
        body: () -> T,
    ): T? {
        // java.lang.Object has no static fields, but has non-trivial initializer
        if (type == ctx.cp.objectType) {
            return body()
        }

        if (!classInitializerAnalysisRequired && !classInitializerAnalysisAlwaysRequiredForType(type)) {
            return body()
        }

        val jcClass = type.jcClass
        val initializer = jcClass.declaredMethods.firstOrNull { it.isClassInitializer }

        // Class has no static initializer
        if (initializer == null) {
            return body()
        }

        val classRef = simpleValueResolver.resolveClassRef(type)

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

                    mutatePrimitiveFieldValuesToSymbolic(scope, initializer)
                    if (jcClass.isEnum) {
                        ensureEnumStaticInitializerInvariants(jcClass)
                    }
                }
            }

            return body()
        }

        // Run static initializer before the current statement
        scope.doWithState {
            memory.write(initializedFlag, ctx.trueExpr)
        }
        scope.doWithState { addConcreteMethodCallStmt(initializer, emptyList()) }
        return null
    }

    private fun staticFieldsInitializedFlag(type: JcRefType, classRef: UHeapRef) =
        UFieldLValue(
            sort = ctx.booleanSort,
            field = JcFieldImpl(type.jcClass, staticFieldsInitializedFlagField),
            ref = classRef
        )

    private fun resolveArrayAccess(array: JcValue, index: JcValue): UArrayIndexLValue<JcType, *, USizeSort>? = with(ctx) {
        val arrayRef = resolveJcExpr(array)?.asExpr(addressSort) ?: return null
        checkNullPointer(arrayRef) ?: return null

        val arrayDescriptor = arrayDescriptorOf(array.type as JcArrayType)

        val idx = resolvePrimitiveCast(index, ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        val lengthRef = UArrayLengthLValue(arrayRef, arrayDescriptor, sizeSort)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }

        assertHardMaxArrayLength(length) ?: return null

        checkArrayIndex(idx, length) ?: return null

        val elementType = requireNotNull(array.type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        return UArrayIndexLValue(cellSort, arrayRef, idx, arrayDescriptor)
    }

    // endregion

    // region implicit exceptions

    fun allocateException(type: JcRefType): (JcState) -> Unit = { state ->
        // TODO should we consider exceptions with negative addresses?
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    fun checkArrayIndex(idx: UExpr<USizeSort>, length: UExpr<USizeSort>) = with(ctx) {
        val inside = (mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and (mkBvSignedLessExpr(idx, length))

        scope.fork(
            inside,
            blockOnFalseState = allocateException(arrayIndexOutOfBoundsExceptionType)
        )
    }

    fun checkNewArrayLength(length: UExpr<USizeSort>) = with(ctx) {
        assertHardMaxArrayLength(length) ?: return null

        val lengthIsNonNegative = mkBvSignedLessOrEqualExpr(mkBv(0), length)

        scope.fork(
            lengthIsNonNegative,
            blockOnFalseState = allocateException(negativeArraySizeExceptionType)
        )
    }

    fun checkDivisionByZero(expr: UExpr<out USort>) = with(ctx) {
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

    fun checkNullPointer(ref: UHeapRef) = with(ctx) {
        val neqNull = mkHeapRefEq(ref, nullRef).not()
        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(nullPointerExceptionType)
        )
    }

    // endregion

    // region hard assertions

    private fun assertHardMaxArrayLength(length: UExpr<USizeSort>): Unit? = with(ctx) {
        val lengthLeThanMaxLength = mkBvSignedLessOrEqualExpr(length, mkBv(hardMaxArrayLength))
        scope.assert(lengthLeThanMaxLength)
            .logAssertFailure { "JcExprResolver: array length max" }
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
        check(type !is JcTypeVariable) {
            "Unexpected type variable $type"
        }

        return if (!ctx.typeSystem<JcType>().isSupertype(type, typeBefore)) {
            val isExpr = scope.calcOnState { memory.types.evalIsSubtype(expr, type) }

            scope.fork(
                isExpr,
                blockOnFalseState = allocateException(ctx.classCastExceptionType)
            ) ?: return null
            expr
        } else {
            expr
        }
    }

    private fun resolvePrimitiveCast(
        expr: JcExpr,
        type: JcPrimitiveType
    ): UExpr<out USort>? = resolveAfterResolved(expr) {
        val exprType = expr.type
        check(exprType is JcPrimitiveType) {
            "Trying cast not primitive type $exprType to primitive type $type"
        }

        resolvePrimitiveCast(it, exprType, type)
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

    /**
     * Always use negative addresses in enum static initializers and enum methods
     * that were reached from the corresponding static initializer, for static initializers of other classes
     * depends on [JcContext.useNegativeAddressesInStaticInitializer].
     */
    fun JcState.useStaticAddressForAllocation(): Boolean {
        val staticInitializers = callStack.filter { it.method.isClassInitializer }

        // Enum's static initializer may contain invocations of other methods – from this enum or from other classes.
        // In case of enum methods, we need to consider all refs allocated in these methods as static too. It is important
        // because these refs may be assigned to enum's static fields – $VALUES, for example.
        val currentClass = lastEnteredMethod.enclosingClass
        val inEnumMethodFromEnumStaticInitializer =
            currentClass.isEnum && staticInitializers.any { it.method.enclosingClass == currentClass }

        if (inEnumMethodFromEnumStaticInitializer) {
            return true
        }

        return ctx.useNegativeAddressesInStaticInitializer && staticInitializers.isNotEmpty()
    }

    /**
     * Consider all mutable primitive fields allocated in the static initializer as symbolic values.
     */
    private fun mutatePrimitiveFieldValuesToSymbolic(
        scope: JcStepScope,
        staticInitializer: JcMethod
    ) {
        scope.calcOnState {
            with(ctx) {
                primitiveTypes.forEach {
                    val sort = typeToSort(it)

                    if (sort === voidSort) return@forEach

                    val memoryRegion = memory.getRegion(JcStaticFieldRegionId(sort)) as JcStaticFieldsMemoryRegion<*>
                    memoryRegion.mutatePrimitiveStaticFieldValuesToSymbolic(this@calcOnState, staticInitializer.enclosingClass)
                }
            }
        }
    }

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

class JcSimpleValueResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    private val localToIdx: (JcMethod, JcLocal) -> Int,
    private val mkTypeRef: (JcType) -> UConcreteHeapRef,
    private val mkStringConstRef: (String) -> UConcreteHeapRef,
) : JcExprVisitor<UExpr<out USort>> {
    override fun visitJcArgument(value: JcArgument): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

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
            // Equal string constants always have equal references
            val ref = resolveStringConstant(value.value)
            val stringValueLValue = UFieldLValue(addressSort, ref, stringValueField.field)

            // String.value type depends on the JVM version
            val charValues = when (stringValueField.fieldType.ifArrayGetElementType) {
                cp.char -> value.value.asSequence().map { mkBv(it.code, charSort) }
                cp.byte -> value.value.encodeToByteArray().asSequence().map { mkBv(it, byteSort) }
                else -> error("Unexpected string values type: ${stringValueField.fieldType}")
            }

            val valuesArrayDescriptor = arrayDescriptorOf(stringValueField.fieldType as JcArrayType)
            val elementType = requireNotNull(stringValueField.fieldType.ifArrayGetElementType)
            val charArrayRef = memory.allocateArrayInitialized(
                valuesArrayDescriptor,
                typeToSort(elementType),
                sizeSort,
                charValues.uncheckedCast()
            )

            // overwrite array type because descriptor is element type
            memory.types.allocate(charArrayRef.address, stringValueField.fieldType)

            // String constants are immutable. Therefore, it is correct to overwrite value, coder and type.
            memory.write(stringValueLValue, charArrayRef)

            // Write coder only if it is presented (depends on the JVM version)
            stringCoderField?.let {
                val stringCoderLValue = UFieldLValue(byteSort, ref, it.field)
                memory.write(stringCoderLValue, mkBv(0, byteSort))
            }

            memory.types.allocate(ref.address, stringType)

            ref
        }
    }


    override fun visitJcClassConstant(value: JcClassConstant): UExpr<out USort> =
        resolveClassRef(value.klass)

    override fun visitJcMethodConstant(value: JcMethodConstant): UExpr<out USort> {
        TODO("Method constant")
    }

    override fun visitJcMethodType(value: JcMethodType): UExpr<out USort> {
        TODO("Method type")
    }

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcThis(value: JcThis): UExpr<out USort> = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitExternalJcExpr(expr: JcExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcAddExpr(expr: JcAddExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcAndExpr(expr: JcAndExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcArrayAccess(value: JcArrayAccess): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcCmpExpr(expr: JcCmpExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcCmplExpr(expr: JcCmplExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcDivExpr(expr: JcDivExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcEqExpr(expr: JcEqExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcGeExpr(expr: JcGeExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcGtExpr(expr: JcGtExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcLeExpr(expr: JcLeExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcLtExpr(expr: JcLtExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcMulExpr(expr: JcMulExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcNegExpr(expr: JcNegExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcNeqExpr(expr: JcNeqExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcOrExpr(expr: JcOrExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcPhiExpr(expr: JcPhiExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcRemExpr(expr: JcRemExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcShlExpr(expr: JcShlExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcShrExpr(expr: JcShrExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcSubExpr(expr: JcSubExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcUshrExpr(expr: JcUshrExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    override fun visitJcXorExpr(expr: JcXorExpr): UExpr<out USort> =
        error("Simple expr resolver must resolve only inheritors of ${JcSimpleValue::class}.")

    fun resolveLocal(local: JcLocal): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterStackLValue(sort, localIdx)
    }

    fun resolveClassRef(type: JcType): UConcreteHeapRef = scope.calcOnState {
        val ref = mkTypeRef(type)
        val classRefTypeLValue = UFieldLValue(ctx.addressSort, ref, ctx.classTypeSyntheticField)

        // Ref type is java.lang.Class
        memory.types.allocate(ref.address, ctx.classType)

        // Save ref original class type with the negative address
        val classRefType = memory.allocStatic(type)
        memory.write(classRefTypeLValue, classRefType)

        ref
    }


    fun resolveStringConstant(value: String): UConcreteHeapRef =
        scope.calcOnState {
            mkStringConstRef(value)
        }
}