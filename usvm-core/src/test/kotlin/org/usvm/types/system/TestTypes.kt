package org.usvm.types.system

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