package org.usvm.bridge

import com.sun.jna.Native
import com.sun.jna.Structure
import org.usvm.api.*
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoMethodInfo
import org.usvm.domain.GoType
import org.usvm.util.GoResult
import org.usvm.util.Path

class GoJnaBridge : Bridge {
    private val path = Path.getLib("java_jna_bridge.so")
    private val jnaBridge = Native.load(path, JnaBridge::class.java)

    private lateinit var api: GoApi

    init {
        System.load(path)
    }

    // ------------ region: init

    override fun initialize(file: String, entrypoint: String, debug: Boolean): GoResult {
        return toResult(jnaBridge.initialize(GoString(file), GoString(entrypoint), debug))
    }

    // ------------ region: init

    // ------------ region: shutdown

    override fun shutdown(): GoResult {
        return toResult(jnaBridge.shutdown())
    }

    // ------------ region: shutdown

    // ------------ region: machine

    override fun getMain(): GoMethod = toMethod(jnaBridge.getMain())

    override fun getMethod(name: String): GoMethod = toMethod(jnaBridge.getMethod(GoString(name)))

    // ------------ region: machine

    // ------------ region: application graph

    override fun predecessors(inst: GoInst): Array<GoInst> = toInstArray(jnaBridge.predecessors(inst.pointer))

    override fun successors(inst: GoInst): Array<GoInst> = toInstArray(jnaBridge.successors(inst.pointer))

    override fun callees(inst: GoInst): Array<GoMethod> = toMethodArray(jnaBridge.callees(inst.pointer))

    override fun callers(method: GoMethod): Array<GoInst> = toInstArray(jnaBridge.callers(method.pointer))

    override fun entryPoints(method: GoMethod): Array<GoInst> = toInstArray(jnaBridge.entryPoints(method.pointer))

    override fun exitPoints(method: GoMethod): Array<GoInst> = toInstArray(jnaBridge.exitPoints(method.pointer))

    override fun methodOf(inst: GoInst): GoMethod = toMethod(jnaBridge.methodOf(inst.pointer))

    override fun statementsOf(method: GoMethod): Array<GoInst> = toInstArray(jnaBridge.statementsOf(method.pointer))

    // ------------ region: application graph

    // ------------ region: type system

    override fun getAnyType(): GoType = toType(jnaBridge.getAnyType())

    override fun findSubTypes(type: GoType): Array<GoType> = toTypeArray(jnaBridge.findSubTypes(type.pointer))

    override fun isInstantiable(type: GoType): Boolean = jnaBridge.isInstantiable(type.pointer)

    override fun isFinal(type: GoType): Boolean = jnaBridge.isFinal(type.pointer)

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean =
        jnaBridge.hasCommonSubtype(type.pointer, toTypeGoSlice(types.toTypedArray()))

    override fun isSupertype(supertype: GoType, type: GoType): Boolean =
        jnaBridge.isSupertype(supertype.pointer, type.pointer)

    // ------------ region: type system

    // ------------ region: interpreter

    override fun methodInfo(method: GoMethod): GoMethodInfo = toMethodInfo(jnaBridge.methodInfo(method.pointer))

    // ------------ region: interpreter

    // ------------ region: api

    fun withApi(api: GoApi): GoJnaBridge = this.also { it.api = api }

    override fun start(): Int = jnaBridge.start(toApi(api))

    override fun step(inst: GoInst): GoInst = toInst(jnaBridge.step(toApi(api), inst.pointer))

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
        return GoMethodInfo(methodInfo.parametersCount, methodInfo.localsCount)
    }

    private fun toType(type: Type): GoType = GoType(type.pointer, type.name)

    private fun toString(string: GoString): String = string.p

    private fun toResult(result: Result): GoResult = GoResult(result.message, result.code)

    private fun toApi(api: GoApi): Api {
        return Api(
            mkIntRegisterReading = object : MkIntRegisterReading {
                override fun mkIntRegisterReading(name: String, idx: Int) {
                    return api.mkIntRegisterReading(idx)
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
                    return api.mkIf(name, toInst(posInst), toInst(negInst))
                }
            },
            mkReturn = object : MkReturn {
                override fun mkReturn(name: String) {
                    return api.mkReturn(name)
                }
            },
            mkVariable = object : MkVariable {
                override fun mkVariable(name: String, value: String) {
                    return api.mkVariable(name, value)
                }
            },
            getLastBlock = object : GetLastBlock {
                override fun getLastBlock(): Int {
                    return api.getLastBlock()
                }
            },
            setLastBlock = object : SetLastBlock {
                override fun setLastBlock(block: Int) {
                    api.setLastBlock(block)
                }
            }
        )
    }

    private fun toObject(obj: Reference, clazz: Class<*>): Object {
        return Object(obj, clazz.name.replace('.', '/'), clazz.isArray)
    }

    // ------------ region: util

    // ------------ region: test

    fun talk(): GoResult = toResult(jnaBridge.talk())

    fun getCalls(): Int = jnaBridge.getCalls()

    fun inc() = jnaBridge.inc()

    fun interpreter() = println(jnaBridge.interpreter())

    fun hello() = jnaBridge.hello()

    fun getBridge(): Long = jnaBridge.getBridge()

    fun getBridgeCalls(pointer: Long): Int = jnaBridge.getBridgeCalls(pointer)

    fun getMainPointer(): Long = jnaBridge.getMainPointer()

    fun getMethodName(pointer: Long): String = toString(jnaBridge.getMethodName(pointer))

    fun countStatementsOf(pointer: Long): Int = jnaBridge.countStatementsOf(pointer)

    fun methods(): Method.ByReference = jnaBridge.methods()

    fun slice(): Slice = jnaBridge.slice()

    fun methodsSlice(): Array<GoMethod> = toMethodArray(jnaBridge.methodsSlice())

    fun callJavaMethod(obj: Reference, clazz: Class<*>) {
        jnaBridge.callJavaMethod(toObject(obj, clazz))
    }

    fun frameStep(api: GoApi): Boolean = jnaBridge.frameStep(toApi(api))

    fun stepRef(api: ApiRef): Boolean = jnaBridge.stepRef(toObject(api, api::class.java))

    fun getNumber(): Int = jnaBridge.getNumber()

    companion object {
        private var i: Int = 0

        @Suppress("unused")
        @JvmStatic
        fun increase() {
            println("INCREASE")
            i++
        }

        @JvmStatic
        fun getStaticNumber(): Int = i
    }

    // ------------ region: test
}
