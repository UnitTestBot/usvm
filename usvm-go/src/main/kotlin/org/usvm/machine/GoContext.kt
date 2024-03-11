package org.usvm.machine

import io.ksmt.utils.asExpr
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UAddressPointer
import org.usvm.UBvSort
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.api.UnknownSortException
import org.usvm.machine.operator.GoUnaryOperator
import org.usvm.machine.type.GoType
import org.usvm.machine.type.GoSort
import org.usvm.machine.type.GoVoidSort
import org.usvm.machine.type.GoVoidValue
import org.usvm.memory.ULValue

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var methodInfo: MutableMap<GoMethod, GoMethodInfo> = hashMapOf()
    private var freeVariables: MutableMap<GoMethod, Array<UExpr<USort>>> = hashMapOf()
    private var deferred: MutableMap<GoMethod, ArrayDeque<GoCall>> = hashMapOf()

    fun getArgsCount(method: GoMethod): Int = methodInfo[method]!!.parametersCount

    fun getReturnType(method: GoMethod): GoType = methodInfo[method]!!.returnType

    fun getMethodInfo(method: GoMethod) = methodInfo[method]!!

    fun setMethodInfo(method: GoMethod, info: GoMethodInfo) {
        methodInfo[method] = info
    }

    fun getFreeVariables(method: GoMethod): Array<UExpr<USort>>? = freeVariables[method]

    fun getFreeVariablesCount(method: GoMethod): Int = getFreeVariables(method)?.size ?: 0

    fun setFreeVariables(method: GoMethod, freeVariables: Array<UExpr<USort>>) {
        this.freeVariables[method] = freeVariables
    }

    fun getDeferred(method: GoMethod): ArrayDeque<GoCall> = deferred[method]!!

    fun addDeferred(method: GoMethod, call: GoCall) {
        if (method !in deferred) {
            deferred[method] = ArrayDeque()
        }
        deferred[method]!!.addLast(call)
    }

    fun mkAddressPointer(address: UConcreteHeapAddress): UExpr<USort> {
        return UAddressPointer(this, address).asExpr(pointerSort)
    }

    fun mkLValuePointer(lvalue: ULValue<*, *>): UExpr<USort> {
        return ULValuePointer(this, lvalue).asExpr(pointerSort)
    }

    val voidSort by lazy { GoVoidSort(this) }

    val voidValue by lazy { GoVoidValue(this) }

    val stringSort by lazy { addressSort }

    fun mapSort(sort: GoSort): USort = when (sort) {
        GoSort.VOID -> voidSort
        GoSort.BOOL -> boolSort
        GoSort.INT8, GoSort.UINT8 -> bv8Sort
        GoSort.INT16, GoSort.UINT16 -> bv16Sort
        GoSort.INT32, GoSort.UINT32 -> bv32Sort
        GoSort.INT64, GoSort.UINT64 -> bv64Sort
        GoSort.FLOAT32 -> fp32Sort
        GoSort.FLOAT64 -> fp64Sort
        GoSort.STRING -> stringSort
        GoSort.ARRAY, GoSort.SLICE, GoSort.MAP, GoSort.STRUCT, GoSort.INTERFACE, GoSort.TUPLE, GoSort.FUNCTION -> addressSort
        GoSort.POINTER -> pointerSort
        else -> throw UnknownSortException()
    }

    fun mkPrimitiveCast(expr: UExpr<USort>, to: USort): UExpr<out USort> = when (to) {
        boolSort -> GoUnaryOperator.CastToBool(expr)
        bv8Sort -> GoUnaryOperator.CastToInt8(expr)
        bv16Sort -> GoUnaryOperator.CastToInt16(expr)
        bv32Sort -> GoUnaryOperator.CastToInt32(expr)
        bv64Sort -> GoUnaryOperator.CastToInt64(expr)
        fp32Sort -> GoUnaryOperator.CastToFloat32(expr)
        fp64Sort -> GoUnaryOperator.CastToFloat64(expr)
        else -> throw IllegalStateException()
    }

    fun mkNarrow(expr: UExpr<UBvSort>, sizeBits: Int, signed: Boolean): UExpr<UBvSort> {
        val diff = sizeBits - expr.sort.sizeBits.toInt()
        val res = if (diff > 0) {
            if (signed) {
                expr.ctx.mkBvSignExtensionExpr(diff, expr)
            } else {
                expr.ctx.mkBvZeroExtensionExpr(diff, expr)
            }
        } else {
            expr.ctx.mkBvExtractExpr(high = sizeBits - 1, low = 0, expr)
        }
        return res
    }

    private val arrayTypeToSliceType: MutableMap<GoType, GoType> = hashMapOf()

    fun getSliceType(arrayType: GoType): GoType? = arrayTypeToSliceType[arrayType]

    fun setSliceType(arrayType: GoType, sliceType: GoType) {
        arrayTypeToSliceType[arrayType] = sliceType
    }
}
