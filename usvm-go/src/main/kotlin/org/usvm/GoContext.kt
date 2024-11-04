package org.usvm

import io.ksmt.utils.asExpr
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.jacodb.go.api.PointerType
import org.usvm.memory.ULValue
import org.usvm.type.GoVoidSort
import org.usvm.type.GoVoidValue

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private val methodInfo: MutableMap<GoMethod, GoMethodInfo> = hashMapOf()
    private val globals: MutableMap<GoGlobal, UExpr<out USort>> = hashMapOf()

    fun getMethodInfo(method: GoMethod) = methodInfo[method]!!

    fun setMethodInfo(method: GoMethod, info: GoMethodInfo) {
        methodInfo[method] = info
    }

    fun setMethodInfo(method: GoMethod, parameters: Array<UExpr<out USort>>) {
        val localsCount = method.blocks.flatMap { it.instructions }.filterIsInstance<GoAssignInst>().size
        val freeVariablesCount = getFreeVariablesCount(method)
        setMethodInfo(method, GoMethodInfo(localsCount + freeVariablesCount, parameters.size))
    }

    fun addGlobal(global: GoGlobal, expr: UExpr<out USort>) {
        globals[global] = expr
    }

    fun getGlobal(global: GoGlobal): UExpr<out USort> = globals[global]!!

    fun freeVariableOffset(method: GoMethod) = getArgsCount(method)

    fun localVariableOffset(method: GoMethod) = getArgsCount(method) + getFreeVariablesCount(method)

    private fun getArgsCount(method: GoMethod): Int = methodInfo[method]!!.argumentsCount

    private fun getFreeVariablesCount(method: GoMethod): Int = when (method) {
        is GoFunction -> method.freeVars.size
        else -> 0
    }

    fun mkAddressPointer(address: UConcreteHeapAddress): UExpr<USort> {
        return UAddressPointer(this, address).asExpr(pointerSort)
    }

    fun mkLValuePointer(lvalue: ULValue<*, *>): UExpr<USort> {
        return ULValuePointer(this, lvalue).asExpr(pointerSort)
    }

    val voidSort by lazy { GoVoidSort(this) }

    val voidValue by lazy { GoVoidValue(this) }

    fun typeToSort(type: GoType): USort = when (type) {
        is BasicType -> basicTypeToSort(type.typeName)
        is PointerType -> pointerSort
        else -> addressSort
    }

    private fun basicTypeToSort(typeName: String): USort = when (typeName) {
        "bool" -> boolSort
        "int", "uint" -> bv32Sort
        "int8", "uint8" -> bv8Sort
        "int16", "uint16" -> bv16Sort
        "int32", "uint32" -> bv32Sort
        "int64", "uint64" -> bv64Sort
        "float32" -> fp32Sort
        "float64" -> fp64Sort
        else -> addressSort
    }

    fun <T : USort> ULValue<*, *>.withSort(sort: T): ULValue<*, T> {
        check(this@withSort.sort == sort) { "Sort mismatch" }

        @Suppress("UNCHECKED_CAST")
        return this@withSort as ULValue<*, T>
    }
}