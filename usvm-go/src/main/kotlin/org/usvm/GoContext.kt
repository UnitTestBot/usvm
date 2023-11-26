package org.usvm

import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.NullType
import org.jacodb.go.api.PointerType
import org.usvm.operator.GoUnaryOperator
import org.usvm.type.GoBasicTypes
import org.usvm.type.GoVoidSort
import org.usvm.type.GoVoidValue

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    val voidSort by lazy { GoVoidSort(this) }

    val voidValue by lazy { GoVoidValue(this) }

    val noValue = mkConst("nothing", voidSort)

    private val methodInfo: MutableMap<GoMethod, GoMethodInfo> = hashMapOf()
    private val globals: MutableMap<GoGlobal, UExpr<out USort>> = hashMapOf()

    fun getMethodInfo(method: GoMethod) = methodInfo[method]!!

    fun setMethodInfo(method: GoMethod) {
        val localsCount = method.blocks.flatMap { it.instructions }.filterIsInstance<GoAssignInst>().size
        val argumentsCount = method.parameters.size

        setMethodInfo(method, GoMethodInfo(localsCount, argumentsCount, emptyArray()))
    }

    fun setMethodInfo(method: GoMethod, parameters: Array<UExpr<out USort>>) {
        val localsCount = method.blocks.flatMap { it.instructions }.filterIsInstance<GoAssignInst>().size
        val freeVariablesCount = getFreeVariablesCount(method)
        setMethodInfo(method, GoMethodInfo(localsCount + freeVariablesCount, parameters.size, parameters))
    }

    fun addGlobal(global: GoGlobal, expr: UExpr<out USort>) {
        globals[global] = expr
    }

    fun getGlobal(global: GoGlobal): UExpr<out USort> = globals[global]!!

    fun freeVariableOffset(method: GoMethod) = getArgsCount(method)

    fun localVariableOffset(method: GoMethod) = getArgsCount(method) + getFreeVariablesCount(method)

    fun typeToSort(type: GoType): USort = when (type) {
        is BasicType -> basicTypeToSort(type)
        is NamedType -> addressSort
        is PointerType -> addressSort
        is NullType -> voidSort
        else -> addressSort
    }

    fun mkPrimitiveCast(expr: UExpr<out USort>, to: USort): UExpr<out USort> = when (to) {
        boolSort -> GoUnaryOperator.CastToBool(expr)
        bv8Sort -> GoUnaryOperator.CastToInt8(expr)
        bv16Sort -> GoUnaryOperator.CastToInt16(expr)
        bv32Sort -> GoUnaryOperator.CastToInt32(expr)
        bv64Sort -> GoUnaryOperator.CastToInt64(expr)
        fp32Sort -> GoUnaryOperator.CastToFloat32(expr)
        fp64Sort -> GoUnaryOperator.CastToFloat64(expr)
        else -> throw IllegalStateException()
    }

    private fun setMethodInfo(method: GoMethod, info: GoMethodInfo) {
        methodInfo[method] = info
    }

    private fun getArgsCount(method: GoMethod): Int = methodInfo[method]!!.argumentsCount

    private fun getFreeVariablesCount(method: GoMethod): Int = when (method) {
        is GoFunction -> method.freeVars.size
        else -> 0
    }

    private fun basicTypeToSort(type: BasicType): USort = when (type) {
        GoBasicTypes.BOOL -> boolSort
        GoBasicTypes.INT, GoBasicTypes.UINT, GoBasicTypes.INT32, GoBasicTypes.UINT32, GoBasicTypes.RUNE -> bv32Sort
        GoBasicTypes.INT8, GoBasicTypes.UINT8 -> bv8Sort
        GoBasicTypes.INT16, GoBasicTypes.UINT16 -> bv16Sort
        GoBasicTypes.INT64, GoBasicTypes.UINT64, GoBasicTypes.UINTPTR, GoBasicTypes.UNSAFE_POINTER -> bv64Sort
        GoBasicTypes.FLOAT32 -> fp32Sort
        GoBasicTypes.FLOAT64 -> fp64Sort
        else -> addressSort
    }
}