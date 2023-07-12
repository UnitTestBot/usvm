package org.usvm.types.system

import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class TestTypeSystem : UTypeSystem<TestType> {
    var id = 0
        private set

    val topType = TestOpenClassType(id++)

    private val typeToDirectSubtypes = hashMapOf<TestType, HashSet<TestType>>()
    private val typeToDirectSupertypes = hashMapOf<TestType, HashSet<TestType>>()

    fun registerType(u: TestType) {
        if (!u.isMultipleInheritanceAllowed) {
            registerInheritance(topType, u)
        }
        id++
    }

    private fun unregisterTopTypeInheritance(base: TestType) {
        val topTypeSubtypes = typeToDirectSubtypes.getOrElse(topType) { return }
        topTypeSubtypes.remove(base)
        val baseTypeSupertypes = typeToDirectSupertypes.getOrElse(base) { return }
        baseTypeSupertypes.remove(topType)
    }

    fun registerInheritance(base: TestType, inheritor: TestType) {
        if (!inheritor.isMultipleInheritanceAllowed) {
            unregisterTopTypeInheritance(inheritor)
        }

        val baseTypeSubtypes = typeToDirectSubtypes.getOrPut(base) { hashSetOf() }
        baseTypeSubtypes += inheritor
        require(!base.isFinal)

        val inheritorTypeSupertypes = typeToDirectSupertypes.getOrPut(inheritor) { hashSetOf() }
        inheritorTypeSupertypes += base

        require(!inheritor.isMultipleInheritanceAllowed || inheritor == topType || inheritor.isMultipleInheritanceAllowed)
        require(inheritorTypeSupertypes.count { !it.isMultipleInheritanceAllowed } <= 1)
    }

    override fun isSupertype(u: TestType, t: TestType): Boolean {
        if (u == t || u == topType) {
            return true
        }
        return typeToDirectSupertypes
            .getOrElse(t) { return false }
            .any { isSupertype(u, it) }
    }

    override fun isMultipleInheritanceAllowedFor(t: TestType): Boolean {
        return t.isMultipleInheritanceAllowed
    }

    override fun isFinal(t: TestType): Boolean {
        return t.isFinal
    }

    override fun isInstantiable(t: TestType): Boolean {
        return t.isInstantiable
    }

    override fun findSubtypes(t: TestType): Sequence<TestType> {
        return typeToDirectSubtypes.getOrDefault(t, hashSetOf()).asSequence()
    }

    override fun topTypeStream(): UTypeStream<TestType> {
        return USupportTypeStream.from(this, topType)
    }
}

