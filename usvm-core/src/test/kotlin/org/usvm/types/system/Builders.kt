package org.usvm.types.system

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

@OptIn(ExperimentalContracts::class)
inline fun buildTypeSystem(builder: TestTypeSystemScope.() -> Unit): TestTypeSystem {
    contract {
        callsInPlace(builder, kind = InvocationKind.EXACTLY_ONCE)
    }
    return TestTypeSystemScope().apply(builder).build()
}