@file:OptIn(ExperimentalContracts::class, ExperimentalContracts::class)

package org.usvm.types

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed interface TestType {
    val id: Int
    val isMultipleInheritanceAllowed: Boolean
    val isInstantiable: Boolean
    val isFinal: Boolean
}

class TestInterfaceType(
    override val id: Int,
) : TestType {
    override val isMultipleInheritanceAllowed: Boolean
        get() = true
    override val isInstantiable: Boolean
        get() = false
    override val isFinal: Boolean
        get() = false
}

class TestAbstractClassType(
    override val id: Int,
) : TestType {
    override val isMultipleInheritanceAllowed: Boolean
        get() = false
    override val isInstantiable: Boolean
        get() = false
    override val isFinal: Boolean
        get() = false
}

class TestOpenClassType(
    override val id: Int,
) : TestType {
    override val isMultipleInheritanceAllowed: Boolean
        get() = false
    override val isInstantiable: Boolean
        get() = true

    override val isFinal: Boolean
        get() = false
}

class TestFinalClassType(
    override val id: Int,
) : TestType {
    override val isMultipleInheritanceAllowed: Boolean
        get() = false
    override val isInstantiable: Boolean
        get() = true

    override val isFinal: Boolean
        get() = true
}

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

    fun registerInheritance(base: TestType, inheritor: TestType) {
        val baseTypeSubtypes = typeToDirectSubtypes.getOrPut(base) { hashSetOf() }
        baseTypeSubtypes += inheritor
        require(!base.isFinal)

        val inheritorTypeSupertypes = typeToDirectSupertypes.getOrPut(inheritor) { hashSetOf() }
        inheritorTypeSupertypes += base

        require(!inheritor.isMultipleInheritanceAllowed || inheritor == topType || inheritor.isMultipleInheritanceAllowed)
        require(inheritorTypeSupertypes.count { !it.isMultipleInheritanceAllowed } <= 1)
    }

    override fun isSupertype(u: TestType, t: TestType): Boolean {
        if (u == t) {
            return true
        }
        return typeToDirectSupertypes.getOrDefault(t, hashSetOf(topType)).any { isSupertype(u, t) }
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

class TestTypeSystemScope {
    private val typeSystem = TestTypeSystem()

    fun `interface`(): TestType {
        val type = TestInterfaceType(typeSystem.id)
        typeSystem.registerType(type)
        return type
    }

    fun abstract(): TestType {
        val type = TestAbstractClassType(typeSystem.id)
        typeSystem.registerType(type)
        return type
    }

    val topType get() = typeSystem.topType

    fun open(): TestType {
        val type = TestOpenClassType(typeSystem.id)
        typeSystem.registerType(type)
        return type
    }

    fun final(): TestType {
        val type = TestFinalClassType(typeSystem.id)
        typeSystem.registerType(type)
        return type
    }

    infix fun TestType.implements(other: TestType): TestType {
        typeSystem.registerInheritance(other, this)
        return this
    }

    fun TestType.implements(vararg others: TestType): TestType {
        others.forEach { typeSystem.registerInheritance(it, this) }
        return this
    }

    fun build() = typeSystem
}

inline fun buildTypeSystem(builder: TestTypeSystemScope.() -> Unit): TestTypeSystem {
    contract {
        callsInPlace(builder, kind = InvocationKind.EXACTLY_ONCE)
    }
    return TestTypeSystemScope().apply(builder).build()
}
