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

    override fun isSupertype(supertype: TestType, type: TestType): Boolean {
        if (supertype == type || supertype == topType) {
            return true
        }
        return typeToDirectSupertypes
            .getOrElse(type) { return false }
            .any { isSupertype(supertype, it) }
    }

    override fun isMultipleInheritanceAllowedFor(type: TestType): Boolean {
        return type.isMultipleInheritanceAllowed
    }

    override fun isFinal(type: TestType): Boolean {
        return type.isFinal
    }

    override fun isInstantiable(type: TestType): Boolean {
        return type.isInstantiable
    }

    override fun findSubtypes(type: TestType): Sequence<TestType> {
        return typeToDirectSubtypes.getOrDefault(type, hashSetOf()).asSequence()
    }

    override fun topTypeStream(): UTypeStream<TestType> {
        return USupportTypeStream.from(this, topType)
    }
}

