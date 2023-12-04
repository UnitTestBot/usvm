package org.usvm.bridge

import com.sun.jna.Native
import com.sun.jna.Structure
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethod
import org.usvm.machine.GoMethodInfo
import org.usvm.machine.GoType
import org.usvm.util.GoResult

class GoBridge {
    private val path = "/home/buraindo/libs/java_bridge.so"
    private val bridge = Native.load(path, Bridge::class.java)

    init {
        System.load(path)
    }

    // ------------ region: init

    fun initialize(filename: String): GoResult {
        return toResult(bridge.initialize(GoString(filename)))
    }

    // ------------ region: init

    // ------------ region: machine

    fun getMain(): GoMethod = toMethod(bridge.getMain())

    fun getMethod(name: String): GoMethod = toMethod(bridge.getMethod(GoString(name)))

    // ------------ region: machine

    // ------------ region: application graph

    fun predecessors(inst: GoInst): Array<GoInst> = toInstArray(bridge.predecessors(inst.pointer))

    fun successors(inst: GoInst): Array<GoInst> = toInstArray(bridge.successors(inst.pointer))

    fun callees(inst: GoInst): Array<GoMethod> = toMethodArray(bridge.callees(inst.pointer))

    fun callers(method: GoMethod): Array<GoInst> = toInstArray(bridge.callers(method.pointer))

    fun entryPoints(method: GoMethod): Array<GoInst> = toInstArray(bridge.entryPoints(method.pointer))

    fun exitPoints(method: GoMethod): Array<GoInst> = toInstArray(bridge.exitPoints(method.pointer))

    fun methodOf(inst: GoInst): GoMethod = toMethod(bridge.methodOf(inst.pointer))

    fun statementsOf(method: GoMethod): Array<GoInst> = toInstArray(bridge.statementsOf(method.pointer))

    // ------------ region: application graph

    // ------------ region: type system

    fun getAnyType(): GoType = toType(bridge.getAnyType())

    fun findSubTypes(type: GoType): Array<GoType> = toTypeArray(bridge.findSubTypes(type.pointer))

    fun isInstantiable(type: GoType): Boolean = bridge.isInstantiable(type.pointer)

    fun isFinal(type: GoType): Boolean = bridge.isFinal(type.pointer)

    fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean =
        bridge.hasCommonSubtype(type.pointer, toTypeGoSlice(types.toTypedArray()))

    fun isSupertype(supertype: GoType, type: GoType): Boolean = bridge.isSupertype(supertype.pointer, type.pointer)

    // ------------ region: type system

    // ------------ region: misc

    fun methodInfo(method: GoMethod): GoMethodInfo = toMethodInfo(bridge.methodInfo(method.pointer))

    // ------------ region: misc

    // ------------ region: util

    private val mapType: (Type.ByReference, GoType) -> Unit = { out, type ->
        out.pointer = type.pointer
        out.name = type.name
    }

    private inline fun <reified T, reified R : Structure> toGoSlice(
        array: Array<T>,
        type: Class<R>,
        mapper: (R, T) -> Unit
    ): GoSlice {
        val typeInstance = Structure.newInstance(type)
        val slice = typeInstance.toArray(array.size).map { it as R }.toTypedArray()
        for (i in array.indices) {
            mapper(slice[i], array[i])
            slice[i].write()
        }
        return GoSlice(typeInstance.pointer, slice.size.toLong(), slice.size.toLong())
    }

    private inline fun <reified T : Structure, reified R> toArray(
        slice: Slice,
        type: Class<T>,
        mapper: (T) -> (R)
    ): Array<R> {
        if (slice.length <= 0) {
            return arrayOf()
        }
        val instance = Structure.newInstance(type, slice.data).also { it.read() }
        val array = instance.toArray(slice.length).map { it as T }
        return array.map(mapper).toTypedArray()
    }

    private fun toInstArray(slice: Slice): Array<GoInst> {
        return toArray(slice, Inst.ByReference::class.java) { toInst(it) }
    }

    private fun toMethodArray(slice: Slice): Array<GoMethod> {
        return toArray(slice, Method.ByReference::class.java) { toMethod(it) }
    }

    private fun toTypeArray(slice: Slice): Array<GoType> {
        return toArray(slice, Type.ByReference::class.java) { toType(it) }
    }

    private fun toTypeGoSlice(array: Array<GoType>): GoSlice {
        return toGoSlice(array, Type.ByReference::class.java, mapType)
    }

    private fun toInst(inst: Inst): GoInst = GoInst(inst.pointer, inst.statement)

    private fun toMethod(method: Method): GoMethod = GoMethod(method.pointer, method.name)

    private fun toMethodInfo(methodInfo: MethodInfo): GoMethodInfo {
        val parameters = toTypeArray(methodInfo.parameters)
        return GoMethodInfo(parameters, methodInfo.localsCount)
    }

    private fun toType(type: Type): GoType = GoType(type.pointer, type.name)

    private fun toString(string: GoString): String = string.p

    private fun toResult(result: Result): GoResult = GoResult(result.message, result.code)

    // ------------ region: util

    // ------------ region: test

    fun talk(): GoResult = toResult(bridge.talk())

    fun getCalls(): Int = bridge.getCalls()

    fun inc() = bridge.inc()

    fun interpreter() = println(bridge.interpreter())

    fun hello() = bridge.hello()

    fun getBridge(): Long = bridge.getBridge()

    fun getBridgeCalls(pointer: Long): Int = bridge.getBridgeCalls(pointer)

    fun getMainPointer(): Long = bridge.getMainPointer()

    fun getMethodName(pointer: Long): String = toString(bridge.getMethodName(pointer))

    fun countStatementsOf(pointer: Long): Int = bridge.countStatementsOf(pointer)

    fun methods(): Method.ByReference = bridge.methods()

    fun slice(): Slice = bridge.slice()

    fun methodsSlice(): Array<GoMethod> = toMethodArray(bridge.methodsSlice())

    companion object {
        private var i: Int = 0

        @Suppress("unused")
        @JvmStatic
        fun increase() {
            println("INCREASE")
            i++
        }

        @JvmStatic
        fun getNumber(): Int = i
    }

    // ------------ region: test
}
