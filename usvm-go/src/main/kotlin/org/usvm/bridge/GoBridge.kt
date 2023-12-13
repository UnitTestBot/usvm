package org.usvm.bridge

import com.sun.jna.Native
import com.sun.jna.Structure
import org.usvm.api.*
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoMethodInfo
import org.usvm.domain.GoType
import org.usvm.util.GoResult

class GoBridge {
    private val path = "/home/buraindo/libs/java_bridge.so"
    private val bridge = Native.load(path, Bridge::class.java)

    init {
        System.load(path)
    }

    // ------------ region: init

    fun initialize(file: String, entrypoint: String, debug: Boolean): GoResult {
        return toResult(bridge.initialize(GoString(file), GoString(entrypoint), debug))
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

    // ------------ region: interpreter

    fun methodInfo(method: GoMethod): GoMethodInfo = toMethodInfo(bridge.methodInfo(method.pointer))

    fun stepRef(api: ApiRef): Boolean = bridge.stepRef(toObject(api, api::class.java))

    // ------------ region: interpreter

    // ------------ region: api

    fun start(api: GoApi): Int = bridge.start(toApi(api))

    fun step(api: GoApi, inst: GoInst): GoInst = toInst(bridge.step(toApi(api), inst.pointer))

    // ------------ region: api

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

    private fun toApi(api: GoApi): Api {
        return Api(
            mkIntRegisterReading = object : MkIntRegisterReading {
                override fun mkIntRegisterReading(name: String, idx: Int) {
                    return api.mkIntRegisterReading(name, idx)
                }
            },
            mkLess = object : MkLess {
                override fun mkLess(name: String, fst: String, snd: String) {
                    return api.mkLess(name, fst, snd)
                }
            },
            mkGreater = object : MkGreater {
                override fun mkGreater(name: String, fst: String, snd: String) {
                    return api.mkGreater(name, fst, snd)
                }
            },
            mkAdd = object : MkAdd {
                override fun mkAdd(name: String, fst: String, snd: String) {
                    return api.mkAdd(name, fst, snd)
                }
            },
            mkIf = object : MkIf {
                override fun mkIf(name: String, posInst: Inst.ByValue, negInst: Inst.ByValue) {
                    return api.mkIf(name, posInst, negInst)
                }
            },
            mkReturn = object : MkReturn {
                override fun mkReturn(name: String) {
                    return api.mkReturn(name)
                }
            },
        )
    }

    private fun toObject(obj: Reference, clazz: Class<*>): Object {
        return Object(obj, clazz.name.replace('.', '/'), clazz.isArray)
    }

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

    fun callJavaMethod(obj: Reference, clazz: Class<*>) {
        bridge.callJavaMethod(toObject(obj, clazz))
    }

    fun frameStep(api: GoApi): Boolean = bridge.frameStep(toApi(api))

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
