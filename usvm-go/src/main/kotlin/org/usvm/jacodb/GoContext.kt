package org.usvm.jacodb

import io.ksmt.utils.asExpr
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.UAddressPointer
import org.usvm.UBvSort
import org.usvm.UComponents
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.jacodb.type.GoVoidSort
import org.usvm.jacodb.type.GoVoidValue
import org.usvm.machine.USizeSort
import org.usvm.jacodb.operator.GoUnaryOperator
import org.usvm.memory.ULValue

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private val methodInfo: MutableMap<GoMethod, GoMethodInfo> = hashMapOf()
    private val freeVariables: MutableMap<GoMethod, Array<UExpr<USort>>> = hashMapOf()

    fun getReturnType(method: GoMethod): GoType = methodInfo[method]!!.returnType

    fun getMethodInfo(method: GoMethod) = methodInfo[method]!!

    fun setMethodInfo(method: GoMethod, info: GoMethodInfo) {
        methodInfo[method] = info
    }

    fun getFreeVariables(method: GoMethod): Array<UExpr<USort>>? = freeVariables[method]

    fun setFreeVariables(method: GoMethod, variables: Array<UExpr<USort>>) {
        freeVariables[method] = variables
    }

    fun freeVariableOffset(method: GoMethod) = getArgsCount(method)

    fun localVariableOffset(method: GoMethod) = getArgsCount(method) + getFreeVariablesCount(method)

    private fun getArgsCount(method: GoMethod): Int = methodInfo[method]!!.parametersCount

    private fun getFreeVariablesCount(method: GoMethod): Int = getFreeVariables(method)?.size ?: 0

    fun mkAddressPointer(address: UConcreteHeapAddress): UExpr<USort> {
        return UAddressPointer(this, address).asExpr(pointerSort)
    }

    fun mkLValuePointer(lvalue: ULValue<*, *>): UExpr<USort> {
        return ULValuePointer(this, lvalue).asExpr(pointerSort)
    }

    val voidSort by lazy { GoVoidSort(this) }

    val voidValue by lazy { GoVoidValue(this) }

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

    private var stringType: GoType = BasicType("string")

    fun getStringType() = stringType

    fun setStringType(type: GoType) {
        stringType = type
    }

    fun <T : USort> ULValue<*, *>.withSort(sort: T): ULValue<*, T> {
        check(this@withSort.sort == sort) { "Sort mismatch" }

        @Suppress("UNCHECKED_CAST")
        return this@withSort as ULValue<*, T>
    }
}