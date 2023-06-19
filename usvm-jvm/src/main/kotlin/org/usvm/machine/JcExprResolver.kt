package org.usvm.machine

import io.ksmt.expr.KBitVec32Value
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.api.JcRefType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcAddExpr
import org.jacodb.api.cfg.JcAndExpr
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
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
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.usvm.UArrayIndexLValue
import org.usvm.UArrayLengthLValue
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFieldLValue
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.URegisterLValue
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.machine.operator.JcBinOperator
import org.usvm.machine.operator.JcUnaryOperator
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.throwException

class JcExprResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    private val applicationGraph: JcApplicationGraph,
    private val localToIdx: (JcTypedMethod, JcLocal) -> Int,
    private val hardMaxArrayLength: Int = 1_500,
) : JcExprVisitor<UExpr<out USort>?> {
    /**
     * TODO: add comment about null
     */
    fun resolveExpr(value: JcExpr): UExpr<out USort>? {
        return value.accept(this)
    }

    /**
     * TODO: add comment about null
     */
    fun resolveLValue(value: JcValue): ULValue? =
        when (value) {
            is JcFieldRef -> resolveFieldRef(value.instance, value.field)
            is JcArrayAccess -> resolveArrayAccess(value.array, value.index)
            is JcLocal -> resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    override fun visitExternalJcExpr(expr: JcExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    //binary operators

    override fun visitJcAddExpr(expr: JcAddExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Add(lhs, rhs) }

    override fun visitJcSubExpr(expr: JcSubExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Sub(lhs, rhs) }

    override fun visitJcMulExpr(expr: JcMulExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Mul(lhs, rhs) }

    override fun visitJcDivExpr(expr: JcDivExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
            checkDivisionByZero(rhs) ?: return null
            JcBinOperator.Div(lhs, rhs)
        }

    override fun visitJcRemExpr(expr: JcRemExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
            checkDivisionByZero(rhs) ?: return null
            JcBinOperator.Rem(lhs, rhs)
        }

    override fun visitJcShlExpr(expr: JcShlExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcShrExpr(expr: JcShrExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcUshrExpr(expr: JcUshrExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcOrExpr(expr: JcOrExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Or(lhs, rhs) }

    override fun visitJcAndExpr(expr: JcAndExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.And(lhs, rhs) }

    override fun visitJcXorExpr(expr: JcXorExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Xor(lhs, rhs) }

    override fun visitJcEqExpr(expr: JcEqExpr): UExpr<out USort>? = with(ctx) {
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
            if (lhs.sort == addressSort) {
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort))
            } else {
                JcBinOperator.Eq(lhs, rhs)
            }
        }
    }

    override fun visitJcNeqExpr(expr: JcNeqExpr): UExpr<out USort>? = with(ctx) {
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
            if (lhs.sort == addressSort) {
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort)).not()
            } else {
                JcBinOperator.Neq(lhs, rhs)
            }
        }
    }

    override fun visitJcGeExpr(expr: JcGeExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Ge(lhs, rhs) }

    override fun visitJcGtExpr(expr: JcGtExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Gt(lhs, rhs) }

    override fun visitJcLeExpr(expr: JcLeExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Le(lhs, rhs) }

    override fun visitJcLtExpr(expr: JcLtExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Lt(lhs, rhs) }

    override fun visitJcCmpExpr(expr: JcCmpExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Cmp(lhs, rhs) }

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Cmpg(lhs, rhs) }

    override fun visitJcCmplExpr(expr: JcCmplExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Cmpl(lhs, rhs) }

    override fun visitJcNegExpr(expr: JcNegExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.operand) { operand -> JcUnaryOperator.Neg(operand) }

    //endregion

    //region constants

    override fun visitJcBool(value: JcBool): UExpr<out USort> = with(ctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcChar(value: JcChar): UExpr<out USort> = with(ctx) {
        mkBv(value.value.code, bv32Sort)
    }

    override fun visitJcByte(value: JcByte): UExpr<out USort> = with(ctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcShort(value: JcShort): UExpr<out USort> = with(ctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcInt(value: JcInt): UExpr<out USort> = with(ctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcLong(value: JcLong): UExpr<out USort> = with(ctx) {
        mkBv(value.value, bv64Sort)
    }

    override fun visitJcFloat(value: JcFloat): UExpr<out USort> = with(ctx) {
        mkFp32(value.value)
    }

    override fun visitJcDouble(value: JcDouble): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visitJcNullConstant(value: JcNullConstant): UExpr<out USort> = with(ctx) {
        nullRef
    }

    override fun visitJcStringConstant(value: JcStringConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcMethodConstant(value: JcMethodConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcClassConstant(value: JcClassConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    //endregion

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort>? = with(ctx) {
        val operand = resolveExpr(expr.operand) ?: return null
        when (expr.type) {
            cp.boolean -> JcUnaryOperator.CastToBoolean(operand)
            cp.short -> JcUnaryOperator.CastToShort(operand)
            cp.int -> JcUnaryOperator.CastToInt(operand)
            cp.long -> JcUnaryOperator.CastToLong(operand)
            cp.float -> JcUnaryOperator.CastToFloat(operand)
            cp.double -> JcUnaryOperator.CastToDouble(operand)
            cp.byte -> JcUnaryOperator.CastToByte(operand)
            cp.char -> JcUnaryOperator.CastToChar(operand)
            is JcRefType -> TODO()
            else -> error("unexpected cast expression: $expr")
        }
    }

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveExpr(expr.array)?.asExpr(addressSort) ?: return null
        checkNullPointer(ref) ?: return null
        val lengthRef = UArrayLengthLValue(ref, expr.array.type)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) } ?: return null
        assertHardMaxArrayLength(length) ?: return null
        scope.assert(mkBvSignedLessOrEqualExpr(mkBv(0), length)) ?: return null
        length
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val size = resolveExpr(expr.dimensions[0])?.asExpr(sizeSort) ?: return null
        // TODO: other dimensions ( > 1)
        checkNewArrayLength(size) ?: return null
        val ref = scope.calcOnState { memory.malloc(expr.type, size) } ?: return null
        ref
    }

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort>? =
        scope.calcOnState { memory.alloc(expr.type) }

    override fun visitJcPhiExpr(expr: JcPhiExpr): UExpr<out USort> = with(ctx) {
        error("unexpected expr: $expr")
    }

    //region invokes

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): UExpr<out USort>? =
        resolveInvoke(expr.method) {
            val instance = resolveExpr(expr.instance)?.asExpr(ctx.addressSort) ?: return@resolveInvoke null
            checkNullPointer(instance) ?: return@resolveInvoke null
            val arguments = mutableListOf<UExpr<out USort>>(instance)
            expr.args.mapTo(arguments) { resolveExpr(it) ?: return@resolveInvoke null }
            arguments
        }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort>? =
        resolveInvoke(expr.method) {
            // TODO resolve actual method for interface invokes
            val instance = resolveExpr(expr.instance)?.asExpr(ctx.addressSort) ?: return@resolveInvoke null
            checkNullPointer(instance) ?: return@resolveInvoke null
            val arguments = mutableListOf<UExpr<out USort>>(instance)
            expr.args.mapTo(arguments) { resolveExpr(it) ?: return@resolveInvoke null }
            arguments
        }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort>? =
        resolveInvoke(expr.method) {
            expr.args.map { resolveExpr(it) ?: return@resolveInvoke null }
        }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort>? =
        resolveInvoke(expr.method) {
            expr.args.map { resolveExpr(it) ?: return@resolveInvoke null }
        }

    private fun resolveInvoke(
        method: JcTypedMethod,
        resolveArguments: () -> List<UExpr<out USort>>?,
    ): UExpr<out USort>? {
        return when (val result = scope.calcOnState { methodResult } ?: return null) {
            is JcMethodResult.Success -> {
                scope.doWithState { methodResult = JcMethodResult.NoCall }
                result.value
            }

            is JcMethodResult.NoCall -> {
                val arguments = resolveArguments() ?: return null
                scope.doWithState { addNewMethodCall(applicationGraph, method, arguments) }
                null
            }

            is JcMethodResult.Exception -> error("exception should be handled earlier")
        }
    }

    //endregion

    //region jc locals

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcThis(value: JcThis): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcArgument(value: JcArgument): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    //endregion

    //region jc complex values

    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort>? = with(ctx) {
        val ref = resolveFieldRef(value.instance, value.field) ?: return null
        scope.calcOnState { memory.read(ref) }
    }


    override fun visitJcArrayAccess(value: JcArrayAccess): UExpr<out USort>? = with(ctx) {
        val ref = resolveArrayAccess(value.array, value.index) ?: return null
        scope.calcOnState { memory.read(ref) }
    }

    //endregion

    private fun resolveArrayAccess(array: JcValue, index: JcValue): ULValue? = with(ctx) {
        val arrayRef = resolveExpr(array)?.asExpr(addressSort) ?: return null
        checkNullPointer(arrayRef) ?: return null

        val idx = resolveExpr(index)?.asExpr(bv32Sort) ?: return null
        val lengthRef = UArrayLengthLValue(arrayRef, array.type)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) } ?: return null

        assertHardMaxArrayLength(length) ?: return null

        checkArrayIndex(idx, length) ?: return null

        val elementType = requireNotNull(array.type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        return UArrayIndexLValue(cellSort, arrayRef, idx, array.type)
    }

    private fun resolveFieldRef(instance: JcValue?, field: JcTypedField): ULValue? = with(ctx) {
        if (instance != null) {
            val instanceRef = resolveExpr(instance)?.asExpr(addressSort) ?: return null
            checkNullPointer(instanceRef) ?: return null
            val sort = ctx.typeToSort(field.fieldType)
            UFieldLValue(sort, instanceRef, field.field)
        } else {
            val sort = ctx.typeToSort(field.fieldType)
            JcStaticFieldRef(sort, field.field)
            // TODO: can't extend UMemoryBase for now...
        }
    }

    private fun resolveLocal(local: JcLocal): ULValue {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterLValue(sort, localIdx)
    }

    private fun checkArrayIndex(idx: USizeExpr, length: USizeExpr) = with(ctx) {
        val inside = (mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and (mkBvSignedLessExpr(idx, length))

        scope.fork(
            inside,
            blockOnFalseState = {
                val exception = ArrayIndexOutOfBoundsException((models.first().eval(idx) as KBitVec32Value).intValue)
                throwException(exception)
            }
        )
    }

    private fun checkNewArrayLength(length: UExpr<USizeSort>) = with(ctx) {
        assertHardMaxArrayLength(length) ?: return null

        val lengthIsNonNegative = mkBvSignedLessOrEqualExpr(mkBv(0), length)

        scope.fork(lengthIsNonNegative,
            blockOnFalseState = {
                val ln = lastStmt.lineNumber
                val exception = NegativeArraySizeException("[negative array size] $ln")
                throwException(exception)
            }
        )
    }


    private fun checkDivisionByZero(rhs: UExpr<out USort>) = with(ctx) {
        val sort = rhs.sort
        if (sort !is UBvSort) {
            return Unit
        }
        val neqZero = mkEq(rhs.cast(), mkBv(0, sort)).not()
        scope.fork(neqZero,
            blockOnFalseState = {
                val ln = lastStmt.lineNumber
                val exception = ArithmeticException("[division by zero] $ln")
                throwException(exception)
            }
        )
    }

    /**
     * TODO: add comments
     */
    private fun checkNullPointer(ref: UHeapRef) = with(ctx) {
        val neqNull = mkHeapRefEq(ref, nullRef).not()
        scope.fork(
            neqNull,
            blockOnFalseState = {
                val ln = lastStmt.lineNumber
                val exception = NullPointerException("[null pointer dereference] $ln")
                throwException(exception)
            }
        )
    }

    private fun assertHardMaxArrayLength(length: USizeExpr): Unit? = with(ctx) {
        val lengthLeThanMaxLength = mkBvSignedLessOrEqualExpr(length, mkBv(hardMaxArrayLength))
        scope.assert(lengthLeThanMaxLength) ?: return null
        return Unit
    }

    private fun resolveAfterResolved(
        dependency0: JcExpr,
        block: (UExpr<out USort>) -> UExpr<out USort>?,
    ): UExpr<out USort>? {
        val result0 = resolveExpr(dependency0) ?: return null
        return block(result0)
    }

    private inline fun resolveAfterResolved(
        dependency0: JcExpr,
        dependency1: JcExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?,
    ): UExpr<out USort>? {
        val result0 = resolveExpr(dependency0) ?: return null
        val result1 = resolveExpr(dependency1) ?: return null
        return block(result0, result1)
    }
}
