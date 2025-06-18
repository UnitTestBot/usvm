package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.model.EtsYieldExpr
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateArray
import org.usvm.dataflow.ts.infer.tryGetKnownType
import org.usvm.dataflow.ts.util.type
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.isInitialized
import org.usvm.machine.interpreter.markInitialized
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.mkFakeValue
import org.usvm.memory.ULValue
import org.usvm.sizeSort
import org.usvm.util.EtsFieldResolutionResult
import org.usvm.util.EtsHierarchy
import org.usvm.util.createFakeField
import org.usvm.util.isResolved
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsField
import org.usvm.util.throwExceptionWithoutStackFrameDrop

private val logger = KotlinLogging.logger {}

@Suppress("MagicNumber")
const val ADHOC_STRING = 777777.0 // arbitrary string

@Suppress("MagicNumber")
const val ADHOC_STRING__NUMBER = 55555.0 // 'number'

@Suppress("MagicNumber")
const val ADHOC_STRING__STRING = 2222.0 // 'string'

class TsExprResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int?,
    private val hierarchy: EtsHierarchy,
) : EtsEntity.Visitor<UExpr<out USort>?> {

    val simpleValueResolver: TsSimpleValueResolver =
        TsSimpleValueResolver(ctx, scope, localToIdx)

    fun resolve(expr: EtsEntity): UExpr<out USort>? {
        return expr.accept(this)
    }

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        expr: EtsUnaryExpr,
    ): UExpr<out USort>? = resolveUnaryOperator(operator, expr.arg)

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        arg: EtsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(arg) { resolved ->
        with(operator) { ctx.resolve(resolved, scope) }
    }

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        expr: EtsBinaryExpr,
    ): UExpr<out USort>? = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        lhv: EtsEntity,
        rhv: EtsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        with(operator) { ctx.resolve(lhs, rhs, scope) }
    }

    private inline fun <T> resolveAfterResolved(
        dependency: EtsEntity,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolve(dependency) ?: return null
        return block(result)
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolve(dependency0) ?: return null
        val result1 = resolve(dependency1) ?: return null
        return block(result0, result1)
    }

    // region SIMPLE VALUE

    override fun visit(value: EtsLocal): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region CONSTANT

    override fun visit(value: EtsConstant): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region UNARY

    override fun visit(expr: EtsNotExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Not, expr)
    }

    override fun visit(expr: EtsNegExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Neg, expr)
    }

    override fun visit(expr: EtsUnaryPlusExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitNotExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsCastExpr): UExpr<*>? = with(ctx) {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg) ?: return null

        if (arg.sort == fp64Sort) {
            if (arg == mkFp64(ADHOC_STRING)) {
                return mkFp64(ADHOC_STRING__STRING)
            }
            return mkFp64(ADHOC_STRING__NUMBER) // 'number'
        }

        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsVoidExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsAwaitExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsYieldExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region BINARY

    override fun visit(expr: EtsAddExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Add, expr)
    }

    override fun visit(expr: EtsSubExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Sub, expr)
    }

    override fun visit(expr: EtsMulExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Mul, expr)
    }

    override fun visit(expr: EtsAndExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.And, expr)
    }

    override fun visit(expr: EtsOrExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Or, expr)
    }

    override fun visit(expr: EtsDivExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Div, expr)
    }

    override fun visit(expr: EtsRemExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Rem, expr)
    }

    override fun visit(expr: EtsExpExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitAndExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitOrExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitXorExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsLeftShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsNullishCoalescingExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region RELATION

    override fun visit(expr: EtsEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Eq, expr)
    }

    override fun visit(expr: EtsNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Neq, expr)
    }

    override fun visit(expr: EtsStrictEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictEq, expr)
    }

    override fun visit(expr: EtsStrictNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictNeq, expr)
    }

    override fun visit(expr: EtsLtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Lt, expr)
    }

    override fun visit(expr: EtsGtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Gt, expr)
    }

    override fun visit(expr: EtsLtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsGtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            memory.types.evalIsSubtype(arg, expr.checkType)
        }
    }

    // endregion

    // region CALL

    private fun handleNumberIsNaN(arg: UExpr<out USort>): UBoolExpr? = with(ctx) {
        // 21.1.2.4 Number.isNaN ( number )
        // 1. If number is not a Number, return false.
        // 2. If number is NaN, return true.
        // 3. Otherwise, return false.

        if (arg.isFakeObject()) {
            val fakeType = arg.getFakeType(scope)
            val value = arg.extractFp(scope)
            return mkIte(
                condition = fakeType.fpTypeExpr,
                trueBranch = mkFpIsNaNExpr(value),
                falseBranch = mkFalse(),
            )
        }

        if (arg.sort == fp64Sort) {
            mkFpIsNaNExpr(arg.asExpr(fp64Sort))
        } else {
            mkFalse()
        }
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        if (expr.instance.name == "Number") {
            if (expr.callee.name == "isNaN") {
                check(expr.args.size == 1) { "Number.isNaN should have one argument" }
                return resolveAfterResolved(expr.args.single()) { arg ->
                    handleNumberIsNaN(arg)
                }
            }
        }

        if (expr.instance.name == "Logger") {
            return mkUndefinedValue()
        }

        if (expr.callee.name == "toString") {
            return mkFp64(ADHOC_STRING)
        }

        return when (val result = scope.calcOnState { methodResult }) {
            is TsMethodResult.Success -> {
                scope.doWithState { methodResult = TsMethodResult.NoCall }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }

            is TsMethodResult.NoCall -> {
                val instance = run {
                    val resolved = resolve(expr.instance) ?: return null

                    if (resolved.sort != addressSort) {
                        logger.warn { "Calling method on non-ref instance is not yet supported" }
                        scope.assert(falseExpr)
                        return null
                    }

                    resolved.asExpr(addressSort)
                }

                val resolvedArgs = expr.args.map { resolve(it) ?: return null }

                val virtualCall = TsVirtualMethodCallStmt(
                    callee = expr.callee,
                    instance = instance,
                    args = resolvedArgs,
                    returnSite = scope.calcOnState { lastStmt },
                )
                scope.doWithState { newStmt(virtualCall) }

                null
            }
        }
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
        if (expr.callee.name == "Number") {
            check(expr.args.size == 1) { "Number constructor should have exactly one argument" }
            return resolveAfterResolved(expr.args.single()) {
                mkNumericExpr(it, scope)
            }
        }

        return when (val result = scope.calcOnState { methodResult }) {
            is TsMethodResult.Success -> {
                scope.doWithState { methodResult = TsMethodResult.NoCall }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }

            is TsMethodResult.NoCall -> {
                // TODO: spawn VirtualCall when method resolution fails
                val method = resolveStaticMethod(expr.callee)

                if (method == null) {
                    logger.error { "Could not resolve static call: ${expr.callee}" }
                    scope.assert(falseExpr)
                    return null
                }

                val instance = scope.calcOnState { getStaticInstance(method.enclosingClass!!) }

                val resolvedArgs = expr.args.map { resolve(it) ?: return null }

                val concreteCall = TsConcreteMethodCallStmt(
                    callee = method,
                    instance = instance,
                    args = resolvedArgs,
                    returnSite = scope.calcOnState { lastStmt },
                )
                scope.doWithState { newStmt(concreteCall) }

                null
            }
        }
    }

    private fun resolveStaticMethod(
        method: EtsMethodSignature,
    ): EtsMethod? {
        // Perfect signature:
        if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
            val classes = ctx.scene.projectAndSdkClasses.filter { it.name == method.enclosingClass.name }
            if (classes.size != 1) return null
            val clazz = classes.single()
            val methods = clazz.methods.filter { it.name == method.name }
            if (methods.size != 1) return null
            return methods.single()
        }

        // Unknown signature:
        val methods = ctx.scene.projectAndSdkClasses
            .flatMap { it.methods }
            .filter { it.name == method.name }
        if (methods.size == 1) return methods.single()

        // error("Cannot resolve method $method")
        return null
    }

    override fun visit(expr: EtsPtrCallExpr): UExpr<out USort>? {
        // TODO: IMPORTANT do not forget to fill sorts of arguments map
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region ACCESS

    override fun visit(value: EtsArrayAccess): UExpr<out USort>? = with(ctx) {
        val array = resolve(value.array)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(array) ?: return null

        val index = resolve(value.index)?.asExpr(fp64Sort) ?: return null
        val bvIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = index,
            bvSize = 32,
            isSigned = true,
        ).asExpr(sizeSort)

        val arrayType = value.array.type as? EtsArrayType
            ?: error("Expected EtsArrayType, but got ${value.array.type}")
        val sort = typeToSort(arrayType.elementType)

        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = scope.calcOnState { memory.read(lengthLValue) }

        checkNegativeIndexRead(bvIndex) ?: return null
        checkReadingInRange(bvIndex, length) ?: return null

        val expr = if (sort is TsUnresolvedSort) {
            // Concrete arrays with the unresolved sort should consist of fake objects only.
            if (array is UConcreteHeapRef) {
                // Read a fake object from the array.
                val lValue = mkArrayIndexLValue(
                    sort = addressSort,
                    ref = array,
                    index = bvIndex,
                    type = arrayType
                )

                scope.calcOnState { memory.read(lValue) }
            } else {
                // If the array is not concrete, we need to allocate a fake object
                val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
                val boolLValue = mkArrayIndexLValue(boolSort, array, bvIndex, boolArrayType)

                val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
                val fpLValue = mkArrayIndexLValue(fp64Sort, array, bvIndex, numberArrayType)

                val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
                val refLValue = mkArrayIndexLValue(addressSort, array, bvIndex, unknownArrayType)

                scope.calcOnState {
                    val boolValue = memory.read(boolLValue)
                    val fpValue = memory.read(fpLValue)
                    val refValue = memory.read(refLValue)

                    // Read an object from the memory at first,
                    // we don't need to recreate it if it is already a fake object.
                    val fakeObj = if (refValue.isFakeObject()) {
                        refValue
                    } else {
                        mkFakeValue(scope, boolValue, fpValue, refValue).also {
                            lValuesToAllocatedFakeObjects += refLValue to it
                        }
                    }

                    memory.write(refLValue, fakeObj.asExpr(addressSort), guard = trueExpr)

                    fakeObj
                }
            }
        } else {
            val lValue = mkArrayIndexLValue(
                sort = sort,
                ref = array,
                index = bvIndex,
                type = arrayType
            )
            scope.calcOnState { memory.read(lValue) }
        }

        return expr
    }

    fun checkUndefinedOrNullPropertyRead(instance: UHeapRef) = with(ctx) {
        val neqNull = mkAnd(
            mkHeapRefEq(instance, mkUndefinedValue()).not(),
            mkHeapRefEq(instance, mkTsNullValue()).not()
        )

        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    fun checkNegativeIndexRead(index: UExpr<TsSizeSort>) = with(ctx) {
        val condition = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))

        scope.fork(
            condition,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    fun checkReadingInRange(index: UExpr<TsSizeSort>, length: UExpr<TsSizeSort>) = with(ctx) {
        val condition = mkBvSignedLessExpr(index, length)

        scope.fork(
            condition,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    private fun allocateException(type: EtsType): (TsState) -> Unit = { state ->
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun handleFieldRef(
        instance: EtsLocal?,
        instanceRef: UHeapRef,
        field: EtsFieldSignature,
        hierarchy: EtsHierarchy,
    ): UExpr<out USort>? = with(ctx) {
        val resolvedAddr = if (instanceRef.isFakeObject()) instanceRef.extractRef(scope) else instanceRef
        scope.doWithState {
            // If we accessed some field, we make an assumption that
            // this field should present in the object.
            // That's not true in the common case for TS, but that's the decision we made.
            val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
            pathConstraints += memory.types.evalIsSubtype(resolvedAddr, auxiliaryType)
        }

        val etsField = resolveEtsField(instance, field, hierarchy)

        val sort = when (etsField) {
            is EtsFieldResolutionResult.Empty -> {
                logger.error("Field $etsField not found, creating fake field")
                // If we didn't find any real fields, let's create a fake one.
                // It is possible due to mistakes in the IR or if the field was added explicitly
                // in the code.
                // Probably, the right behaviour here is to fork the state.
                resolvedAddr.createFakeField(field.name, scope)
                addressSort
            }

            is EtsFieldResolutionResult.Unique -> typeToSort(etsField.field.type)
            is EtsFieldResolutionResult.Ambiguous -> unresolvedSort
        }

        if (sort == unresolvedSort) {
            val boolLValue = mkFieldLValue(boolSort, instanceRef, field)
            val fpLValue = mkFieldLValue(fp64Sort, instanceRef, field)
            val refLValue = mkFieldLValue(addressSort, instanceRef, field)

            scope.calcOnState {
                val bool = memory.read(boolLValue)
                val fp = memory.read(fpLValue)
                val ref = memory.read(refLValue)

                // If a fake object is already created and assigned to the field,
                // there is no need to recreate another one
                val fakeRef = if (ref.isFakeObject()) {
                    ref
                } else {
                    mkFakeValue(scope, bool, fp, ref).also {
                        lValuesToAllocatedFakeObjects += refLValue to it
                    }
                }
                memory.write(refLValue, fakeRef.asExpr(addressSort), guard = trueExpr)

                fakeRef
            }
        } else {
            val lValue = mkFieldLValue(sort, instanceRef, field)
            scope.calcOnState { memory.read(lValue) }
        }
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort>? = with(ctx) {
        val instanceRef = resolve(value.instance)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(instanceRef) ?: return null

        // TODO It is a hack for array's length
        if (value.field.name == "length") {
            if (value.instance.type is EtsArrayType) {
                val arrayType = value.instance.type as EtsArrayType
                val lengthLValue = mkArrayLengthLValue(instanceRef, arrayType)
                val length = scope.calcOnState { memory.read(lengthLValue) }
                scope.doWithState { pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0)) }

                return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), length.asExpr(sizeSort), signed = true)
            }

            // TODO: handle "length" property for arrays inside fake objects
            if (instanceRef.isFakeObject()) {
                val fakeType = instanceRef.getFakeType(scope)

                // If we want to get length from a fake object, we assume that it is an array.
                scope.doWithState { pathConstraints += fakeType.refTypeExpr }

                val refLValue = getIntermediateRefLValue(instanceRef.address)
                val obj = scope.calcOnState { memory.read(refLValue) }

                val type = value.instance.type
                val arrayType = type as? EtsArrayType ?: run {
                    check(type is EtsAnyType || type is EtsUnknownType) {
                        "Expected EtsArrayType, EtsAnyType or EtsUnknownType, but got $type"
                    }

                    // We don't know the type of the array, since it is a fake object
                    // If we'd know the type, we would have used it instead of creating a fake object
                    EtsArrayType(EtsUnknownType, dimensions = 1)
                }
                val lengthLValue = mkArrayLengthLValue(obj, arrayType)
                val length = scope.calcOnState { memory.read(lengthLValue) }

                scope.doWithState { pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0)) }

                return mkBvToFpExpr(
                    fp64Sort,
                    fpRoundingModeSortDefaultValue(),
                    length.asExpr(sizeSort),
                    signed = true
                )

            }
        }

        return handleFieldRef(value.instance, instanceRef, value.field, hierarchy)
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort>? = with(ctx) {
        val clazz = scene.projectAndSdkClasses.singleOrNull {
            it.signature == value.field.enclosingClass
        } ?: return null

        val instanceRef = scope.calcOnState { getStaticInstance(clazz) }

        val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
        if (initializer != null) {
            val isInitialized = scope.calcOnState { isInitialized(clazz) }
            if (isInitialized) {
                scope.doWithState {
                    // TODO: Handle static initializer result
                    val result = methodResult
                    if (result is TsMethodResult.Success && result.methodSignature() == initializer.signature) {
                        methodResult = TsMethodResult.NoCall
                    }
                }
            } else {
                scope.doWithState {
                    markInitialized(clazz)
                    pushSortsForArguments(instance = null, args = emptyList(), localToIdx)
                    callStack.push(initializer, currentStatement)
                    memory.stack.push(arrayOf(instanceRef), initializer.localsCount)
                    newStmt(initializer.cfg.stmts.first())
                }
                return null
            }
        }

        return handleFieldRef(instance = null, instanceRef, value.field, hierarchy)
    }

    // endregion

    // region OTHER

    override fun visit(expr: EtsNewExpr): UExpr<out USort>? = with(ctx) {
        // Try to resolve the concrete type if possible.
        // Otherwise, create an object with UnclearRefType
        val resolvedType = if (expr.type.isResolved()) {
            scene.projectAndSdkClasses
                .singleOrNull { it.name == expr.type.typeName }?.type
                ?: expr.type
        } else {
            expr.type
        }
        scope.calcOnState { memory.allocConcrete(resolvedType) }
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val arrayType = expr.type

        require(arrayType is EtsArrayType) {
            "Expected EtsArrayType in newArrayExpr, but got ${arrayType::class.simpleName}"
        }

        scope.calcOnState {
            val size = resolve(expr.size) ?: return@calcOnState null

            if (size.sort != fp64Sort) {
                TODO()
            }

            val bvSize = mkFpToBvExpr(
                roundingMode = fpRoundingModeSortDefaultValue(),
                value = size.asExpr(fp64Sort),
                bvSize = 32,
                isSigned = true,
            )

            val condition = mkAnd(
                mkEq(
                    mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), bvSize, signed = true),
                    size.asExpr(fp64Sort)
                ),
                mkAnd(
                    mkBvSignedLessOrEqualExpr(0.toBv(), bvSize.asExpr(bv32Sort)),
                    mkBvSignedLessOrEqualExpr(bvSize, Int.MAX_VALUE.toBv().asExpr(bv32Sort))
                )
            )

            scope.fork(
                condition,
                blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
            )

            if (arrayType.elementType is EtsArrayType) {
                TODO("Multidimensional arrays are not supported yet, https://github.com/UnitTestBot/usvm/issues/287")
            }

            val address = memory.allocateArray(arrayType, sizeSort, bvSize)

            address
        }
    }

    // endregion
}

class TsSimpleValueResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int?,
) : EtsValue.Visitor<UExpr<out USort>?> {

    private fun resolveLocal(local: EtsValue): ULValue<*, USort> {
        val currentMethod = scope.calcOnState { lastEnteredMethod }
        val entrypoint = scope.calcOnState { entrypoint }

        val localIdx = localToIdx(currentMethod, local)

        // If there is no local variable corresponding to the local,
        // we treat it as a field of some global object with the corresponding name.
        // It helps us to support global variables that are missed in the IR.
        if (localIdx == null) {
            require(local is EtsLocal)

            val globalObject = scope.calcOnState { globalObject }

            val localName = local.name
            // Check whether this local was already created or not
            if (localName in scope.calcOnState { addedArtificialLocals }) {
                return mkFieldLValue(ctx.addressSort, globalObject, local.name)
            }

            logger.warn { "Cannot resolve local $local" }

            globalObject.createFakeField(localName, scope)
            scope.doWithState {
                addedArtificialLocals += localName
            }

            return mkFieldLValue(ctx.addressSort, globalObject, local.name)
        }

        val sort = scope.calcOnState {
            val type = local.tryGetKnownType(currentMethod)
            getOrPutSortForLocal(localIdx, type)
        }

        // If we are not in the entrypoint, all correct values are already resolved and we can just return
        // a registerStackLValue for the local
        if (currentMethod != entrypoint) {
            return mkRegisterStackLValue(sort, localIdx)
        }

        // arguments and this for the first stack frame
        return when (sort) {
            is UBoolSort -> mkRegisterStackLValue(sort, localIdx)
            is KFp64Sort -> mkRegisterStackLValue(sort, localIdx)
            is UAddressSort -> mkRegisterStackLValue(sort, localIdx)
            is TsUnresolvedSort -> {
                check(local is EtsThis || local is EtsParameterRef) {
                    "Only This and ParameterRef are expected here"
                }

                val lValue = mkRegisterStackLValue(ctx.addressSort, localIdx)

                val boolRValue = ctx.mkRegisterReading(localIdx, ctx.boolSort)
                val fpRValue = ctx.mkRegisterReading(localIdx, ctx.fp64Sort)
                val refRValue = ctx.mkRegisterReading(localIdx, ctx.addressSort)

                val fakeObject = ctx.mkFakeValue(scope, boolRValue, fpRValue, refRValue)
                scope.calcOnState {
                    with(ctx) {
                        memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
                    }
                }

                lValue
            }

            else -> error("Unsupported sort $sort")
        }
    }

    override fun visit(local: EtsLocal): UExpr<out USort> {
        if (local.name == "NaN") {
            return ctx.mkFp64NaN()
        }
        if (local.name == "Infinity") {
            return ctx.mkFpInf(false, ctx.fp64Sort)
        }

        val currentMethod = scope.calcOnState { lastEnteredMethod }
        if (local !in currentMethod.getDeclaredLocals()) {
            if (local.type is EtsFunctionType) {
                // TODO: function pointers should be "singletons"
                return scope.calcOnState { memory.allocConcrete(local.type) }
            }
        }

        val lValue = resolveLocal(local)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsConstant): UExpr<out USort> = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> = with(ctx) {
        return when (value.value) {
            "number" -> mkFp64(ADHOC_STRING__NUMBER)
            "string" -> mkFp64(ADHOC_STRING__STRING)
            else -> mkFp64(ADHOC_STRING)
        }
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> = with(ctx) {
        mkTsNullValue()
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> = with(ctx) {
        mkUndefinedValue()
    }

    override fun visit(value: EtsArrayAccess): UExpr<out USort>? = with(ctx) {
        error("Should not be called")
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort> = with(ctx) {
        error("Should not be called")
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort> = with(ctx) {
        error("Should not be called")
    }
}
