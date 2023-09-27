package org.usvm.machine

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateArray
import org.usvm.language.And
import org.usvm.language.ArrayCreation
import org.usvm.language.ArrayEq
import org.usvm.language.ArrayExpr
import org.usvm.language.ArrayIdxSetLValue
import org.usvm.language.ArraySelect
import org.usvm.language.ArraySize
import org.usvm.language.ArrayType
import org.usvm.language.BooleanConst
import org.usvm.language.BooleanEq
import org.usvm.language.BooleanExpr
import org.usvm.language.BooleanType
import org.usvm.language.DivisionByZero
import org.usvm.language.Expr
import org.usvm.language.Field
import org.usvm.language.FieldSelect
import org.usvm.language.FieldSetLValue
import org.usvm.language.Ge
import org.usvm.language.Gt
import org.usvm.language.IndexOutOfBounds
import org.usvm.language.IntConst
import org.usvm.language.IntDiv
import org.usvm.language.IntEq
import org.usvm.language.IntExpr
import org.usvm.language.IntMinus
import org.usvm.language.IntPlus
import org.usvm.language.IntRem
import org.usvm.language.IntTimes
import org.usvm.language.IntType
import org.usvm.language.LValue
import org.usvm.language.Le
import org.usvm.language.Lt
import org.usvm.language.NegativeArraySize
import org.usvm.language.Not
import org.usvm.language.NullPointerDereference
import org.usvm.language.Or
import org.usvm.language.Register
import org.usvm.language.RegisterLValue
import org.usvm.language.SampleType
import org.usvm.language.StructCreation
import org.usvm.language.StructEq
import org.usvm.language.StructExpr
import org.usvm.language.StructIsNull
import org.usvm.language.StructType
import org.usvm.language.UnaryMinus
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue

/**
 * Resolves [Expr]s to [UExpr]s, forks in the [scope] respecting unsats. Checks for exceptions.
 *
 * @param hardMaxArrayLength denotes the maximum acceptable array length. All states with any length greater than
 * [hardMaxArrayLength] will be rejected.
 */
class SampleExprResolver(
    private val ctx: UContext<USizeSort>,
    private val scope: SampleStepScope,
    private val hardMaxArrayLength: Int = 1_500,
) {
    fun resolveExpr(expr: Expr<SampleType>): UExpr<out USort>? =
        @Suppress("UNCHECKED_CAST")
        when (expr.type) {
            BooleanType -> resolveBoolean(expr as BooleanExpr)
            IntType -> resolveInt(expr as IntExpr)
            is ArrayType<*> -> resolveArray(expr as ArrayExpr<*>)
            is StructType -> resolveStruct(expr as StructExpr)
        }

    fun resolveStruct(expr: StructExpr): UHeapRef? = with(ctx) {
        when (expr) {
            is StructCreation -> {
                val ref = scope.calcOnState { memory.allocConcrete(expr.type) }

                for ((field, fieldExpr) in expr.fields) {
                    val sort = typeToSort(field.type)
                    val fieldRef = UFieldLValue(sort, ref, field)
                    val fieldUExpr = resolveExpr(fieldExpr) ?: return null

                    scope.doWithState { memory.write(fieldRef, fieldUExpr) }
                }

                ref
            }

            is ArraySelect -> resolveArraySelect(expr)?.asExpr(addressSort)
            is FieldSelect -> resolveFieldSelect(expr)?.asExpr(addressSort)
            is Register -> resolveRegister(expr)?.asExpr(addressSort)
            else -> error("Unexpected StructExpr: $expr")
        }
    }

    fun resolveArray(expr: ArrayExpr<*>): UHeapRef? = with(ctx) {
        when (expr) {
            is ArrayCreation -> {
                val size = resolveInt(expr.size) ?: return null
                checkArrayLength(size, expr.values.size) ?: return null

                val ref = scope.calcOnState { memory.allocateArray(expr.type, sizeSort, size) }

                val cellSort = typeToSort(expr.type.elementType)

                val values = expr.values.map { resolveExpr(it) ?: return null }
                values.forEachIndexed { index, kExpr ->
                    val lvalue = UArrayIndexLValue(cellSort, ref, mkBv(index), expr.type)

                    scope.doWithState { memory.write(lvalue, kExpr) }
                }

                // TODO: memset is not implemented
                // memory.memset(ref, expr.type, cellSort, values)

                ref
            }

            is ArraySelect -> resolveArraySelect(expr)?.asExpr(addressSort)
            is FieldSelect -> resolveFieldSelect(expr)?.asExpr(addressSort)
            is Register -> resolveRegister(expr)?.asExpr(addressSort)
            else -> error("Unexpected ArrayExpr: $expr")
        }
    }

    fun resolveInt(expr: IntExpr): UExpr<UBv32Sort>? = with(ctx) {
        when (expr) {
            is ArraySize -> {
                val ref = resolveArray(expr.array) ?: return null
                checkNullPointer(ref) ?: return null
                val lengthRef = UArrayLengthLValue(ref, expr.array.type, sizeSort)
                val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) }
                checkHardMaxArrayLength(length) ?: return null
                scope.assert(mkBvSignedLessOrEqualExpr(mkBv(0), length)) ?: return null
                length
            }

            is IntConst -> mkBv(expr.const)
            is IntDiv -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                checkDivisionByZero(rhs)
                mkBvSignedDivExpr(lhs, rhs)
            }

            is IntMinus -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvSubExpr(lhs, rhs)
            }

            is IntPlus -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvAddExpr(lhs, rhs)
            }

            is IntRem -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                checkDivisionByZero(rhs)
                mkBvSignedRemExpr(lhs, rhs)
            }

            is IntTimes -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvMulExpr(lhs, rhs)
            }

            is UnaryMinus -> {
                val operand = resolveInt(expr.value) ?: return null
                mkBvNegationExpr(operand)
            }

            is ArraySelect -> resolveArraySelect(expr)?.asExpr(bv32Sort)
            is FieldSelect -> resolveFieldSelect(expr)?.asExpr(bv32Sort)
            is Register -> resolveRegister(expr)?.asExpr(bv32Sort)
            else -> error("Unexpected IntExpr: $expr")
        }
    }

    fun resolveBoolean(expr: BooleanExpr): UBoolExpr? = with(ctx) {
        when (expr) {
            is And -> {
                val lhs = resolveBoolean(expr.left) ?: return null
                val rhs = resolveBoolean(expr.right) ?: return null
                lhs and rhs
            }

            is ArrayEq<*> -> {
                val lhs = resolveArray(expr.left) ?: return null
                val rhs = resolveArray(expr.right) ?: return null
                lhs eq rhs
            }

            is BooleanEq -> {
                val lhs = resolveBoolean(expr.left) ?: return null
                val rhs = resolveBoolean(expr.right) ?: return null
                lhs eq rhs
            }

            is BooleanConst -> expr.const.expr
            is Ge -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvSignedGreaterOrEqualExpr(lhs, rhs)
            }

            is Gt -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvSignedGreaterExpr(lhs, rhs)
            }

            is IntEq -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                lhs eq rhs
            }

            is Le -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvSignedLessOrEqualExpr(lhs, rhs)
            }

            is Lt -> {
                val lhs = resolveInt(expr.left) ?: return null
                val rhs = resolveInt(expr.right) ?: return null
                mkBvSignedLessExpr(lhs, rhs)
            }

            is Not -> {
                val operand = resolveBoolean(expr.value) ?: return null
                operand.not()
            }

            is Or -> {
                val lhs = resolveBoolean(expr.left) ?: return null
                val rhs = resolveBoolean(expr.right) ?: return null
                lhs or rhs
            }

            is StructEq -> {
                val lhs = resolveStruct(expr.left) ?: return null
                val rhs = resolveStruct(expr.right) ?: return null
                lhs eq rhs
            }

            is StructIsNull -> {
                val operand = resolveStruct(expr.struct) ?: return null
                operand eq nullRef
            }

            is ArraySelect -> resolveArraySelect(expr)?.asExpr(boolSort)
            is FieldSelect -> resolveFieldSelect(expr)?.asExpr(boolSort)
            is Register -> resolveRegister(expr)?.asExpr(boolSort)
            else -> error("Unexpected BoolExpr: $expr")
        }
    }

    fun resolveLValue(value: LValue): ULValue<*, *>? =
        when (value) {
            is ArrayIdxSetLValue -> resolveArraySelectRef(value.array, value.index)
            is FieldSetLValue -> resolveFieldSelectRef(value.instance, value.field)
            is RegisterLValue -> resolveRegisterRef(value.value)
        }

    private fun resolveRegister(register: Register<SampleType>): UExpr<out USort>? {
        val registerRef = resolveRegisterRef(register)
        return scope.calcOnState { memory.read(registerRef) }
    }

    private fun <T : SampleType> resolveArraySelect(arraySelect: ArraySelect<T>): UExpr<out USort>? {
        val arrayIndexRef = resolveArraySelectRef(arraySelect.array, arraySelect.index) ?: return null
        return scope.calcOnState { memory.read(arrayIndexRef) }
    }

    private fun <T : SampleType> resolveFieldSelect(fieldSelect: FieldSelect<T>): UExpr<out USort>? {
        val fieldRef = resolveFieldSelectRef(fieldSelect.instance, fieldSelect.field) ?: return null

        return scope.calcOnState { memory.read(fieldRef) }
    }

    private fun resolveArraySelectRef(array: ArrayExpr<*>, index: IntExpr): ULValue<*, *>? {
        val arrayRef = resolveArray(array) ?: return null
        checkNullPointer(arrayRef) ?: return null

        val idx = resolveInt(index) ?: return null
        val lengthRef = UArrayLengthLValue(arrayRef, array.type, ctx.sizeSort)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(ctx.sizeSort) }

        checkHardMaxArrayLength(length) ?: return null

        checkArrayIndex(idx, length) ?: return null

        val cellSort = ctx.typeToSort(array.type.elementType)

        return UArrayIndexLValue(cellSort, arrayRef, idx, array.type)
    }

    private fun resolveFieldSelectRef(instance: StructExpr, field: Field<*>): ULValue<*, *>? {
        val instanceRef = resolveStruct(instance) ?: return null

        checkNullPointer(instanceRef) ?: return null
        val sort = ctx.typeToSort(field.type)
        return UFieldLValue(sort, instanceRef, field)
    }

    private fun resolveRegisterRef(register: Register<*>): ULValue<*, *> {
        val localIdx = register.idx
        val type = register.type
        val sort = ctx.typeToSort(type)
        return URegisterStackLValue(sort, localIdx)
    }

    private fun checkArrayIndex(idx: UExpr<UBv32Sort>, length: UExpr<UBv32Sort>) = with(ctx) {
        val inside = (mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and (mkBvSignedLessExpr(idx, length))

        scope.fork(
            inside,
            blockOnFalseState = {
                exceptionRegister = IndexOutOfBounds(
                    lastStmt,
                    (models.first().eval(length) as KBitVec32Value).intValue,
                    (models.first().eval(idx) as KBitVec32Value).intValue,
                )
            }
        )
    }

    private fun checkArrayLength(length: KExpr<UBv32Sort>, actualLength: Int) = with(ctx) {
        checkHardMaxArrayLength(length) ?: return null

        val actualLengthLeThanLength = mkBvSignedLessOrEqualExpr(mkBv(actualLength), length)

        scope.fork(actualLengthLeThanLength,
            blockOnFalseState = {
                exceptionRegister = NegativeArraySize(
                    lastStmt,
                    (models.first().eval(length) as KBitVec32Value).intValue,
                    actualLength
                )
            }
        )
    }


    private fun checkDivisionByZero(rhs: UExpr<UBv32Sort>) = with(ctx) {
        val neqZero = mkEq(rhs, mkBv(0)).not()
        scope.fork(neqZero,
            blockOnFalseState = {
                exceptionRegister = DivisionByZero(lastStmt)
            }
        )
    }

    private fun checkNullPointer(ref: UHeapRef) = with(ctx) {
        val neqNull = mkHeapRefEq(ref, nullRef).not()
        scope.fork(
            neqNull,
            blockOnFalseState = {
                exceptionRegister = NullPointerDereference(lastStmt)
            }
        )
    }

    private fun checkHardMaxArrayLength(length: UExpr<UBv32Sort>): Unit? = with(ctx) {
        val lengthLeThanMaxLength = mkBvSignedLessOrEqualExpr(length, mkBv(hardMaxArrayLength))
        scope.assert(lengthLeThanMaxLength) ?: return null
        return Unit
    }
}