package org.usvm.bridge

import com.sun.jna.Native
import com.sun.jna.Structure
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethod

class GoBridge {
    private val path = "/home/buraindo/libs/java_bridge.so"
    private val bridge = Native.load(path, Bridge::class.java)

    init {
        System.load(path)
    }

    // ------------ region: init

    fun initialize(filename: String): String { //TODO proper error handling
        return bridge.initialize(GoString(filename)).toString()
    }

    // ------------ region: init

    // ------------ region: machine

    fun getMain(): GoMethod = toMethod(bridge.getMain())

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

    // ------------ region: util

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

    private fun toInst(inst: Inst): GoInst = GoInst(inst.pointer, inst.statement)

    private fun toMethod(method: Method): GoMethod = GoMethod(method.pointer, method.name)

    private fun toString(string: GoString): String = string.p

    // ------------ region: util

    // ------------ region: test

    fun talk(): String = bridge.talk().toString() //TODO proper error handling

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
