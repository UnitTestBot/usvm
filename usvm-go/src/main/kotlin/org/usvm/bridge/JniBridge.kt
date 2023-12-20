package org.usvm.bridge

import org.usvm.api.GoApi
import org.usvm.domain.GoInst
import org.usvm.util.Path

class JniBridge {
    private val path = Path.getLib("java_jni_bridge.so")

    private lateinit var api: GoApi

    init {
        System.load(path)
    }

    // ------------ region: initialize
    external fun initialize(file: ByteArray, entrypoint: ByteArray, debug: Boolean): Int
    // ------------ region: initialize

    // ------------ region: initialize
    external fun shutdown(): Int
    // ------------ region: initialize

    // ------------ region: machine
    external fun getMain(): MethodPointer
    external fun getMethod(name: ByteArray): MethodPointer
    // ------------ region: machine

    // ------------ region: application graph
    external fun predecessors(inst: InstPointer): LongArray
    external fun successors(inst: InstPointer): LongArray
    external fun callees(inst: InstPointer): LongArray
    external fun callers(method: MethodPointer): LongArray
    external fun entryPoints(method: MethodPointer): LongArray
    external fun exitPoints(method: MethodPointer): LongArray
    external fun methodOf(inst: InstPointer): Long
    external fun statementsOf(method: MethodPointer): LongArray
    // ------------ region: application graph

    // ------------ region: type system
    external fun getAnyType(): TypePointer
    external fun findSubTypes(type: TypePointer): LongArray
    external fun isInstantiable(type: TypePointer): Boolean
    external fun isFinal(type: TypePointer): Boolean
    external fun hasCommonSubtype(type: TypePointer, types: LongArray, typesLen: Int): Boolean
    external fun isSupertype(supertype: TypePointer, type: TypePointer): Boolean
    // ------------ region: type system

    // ------------ region: interpreter
    external fun methodInfo(method: MethodPointer): IntArray
    // ------------ region: interpreter

    // ------------ region: api
    external fun start(): Int
    external fun step(inst: InstPointer): InstPointer

    fun withApi(api: GoApi): JniBridge = this.also { it.api = api }

    fun mkIntRegisterReading(idx: Int) {
        api.mkIntRegisterReading(idx)
    }

    fun mkLess(name: ByteArray, fst: ByteArray, snd: ByteArray) {
        api.mkLess(String(name), String(fst), String(snd))
    }

    fun mkGreater(name: ByteArray, fst: ByteArray, snd: ByteArray) {
        api.mkGreater(String(name), String(fst), String(snd))
    }

    fun mkAdd(name: ByteArray, fst: ByteArray, snd: ByteArray) {
        api.mkAdd(String(name), String(fst), String(snd))
    }

    fun mkIf(name: ByteArray, pos: InstPointer, neg: InstPointer) {
        api.mkIf(String(name), GoInst(pos, "pos"), GoInst(neg, "neg"))
    }

    fun mkReturn(name: ByteArray) {
        api.mkReturn(String(name))
    }

    fun mkVariable(name: ByteArray, value: ByteArray) {
        api.mkVariable(String(name), String(value))
    }

    fun getLastBlock(): Int {
        return api.getLastBlock()
    }

    fun setLastBlock(block: Int) {
        api.setLastBlock(block)
    }
    // ------------ region: api

    // ------------ region: test
    external fun getNumber(): Int
    external fun initialize2(file: String, entrypoint: String, debug: Boolean): Long
    // ------------ region: test
}