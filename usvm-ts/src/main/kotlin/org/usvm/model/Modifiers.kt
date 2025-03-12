package org.usvm.model

enum class TsModifier(val value: Int, val string: String) {
    PRIVATE(1 shl 0, "private"),
    PROTECTED(1 shl 1, "protected"),
    PUBLIC(1 shl 2, "public"),
    EXPORT(1 shl 3, "export"),
    STATIC(1 shl 4, "static"),
    ABSTRACT(1 shl 5, "abstract"),
    ASYNC(1 shl 6, "async"),
    CONST(1 shl 7, "const"),
    ACCESSOR(1 shl 8, "accessor"),
    DEFAULT(1 shl 9, "default"),
    IN(1 shl 10, "in"),
    READONLY(1 shl 11, "readonly"),
    OUT(1 shl 12, "out"),
    OVERRIDE(1 shl 13, "override"),
    DECLARE(1 shl 14, "declare");
}

interface WithModifiers {
    val isPrivate: Boolean get() = hasModifier(TsModifier.PRIVATE)
    val isProtected: Boolean get() = hasModifier(TsModifier.PROTECTED)
    val isPublic: Boolean get() = hasModifier(TsModifier.PUBLIC)
    val isExport: Boolean get() = hasModifier(TsModifier.EXPORT)
    val isStatic: Boolean get() = hasModifier(TsModifier.STATIC)
    val isAbstract: Boolean get() = hasModifier(TsModifier.ABSTRACT)
    val isAsync: Boolean get() = hasModifier(TsModifier.ASYNC)
    val isConst: Boolean get() = hasModifier(TsModifier.CONST)
    val isAccessor: Boolean get() = hasModifier(TsModifier.ACCESSOR)
    val isDefault: Boolean get() = hasModifier(TsModifier.DEFAULT)
    val isIn: Boolean get() = hasModifier(TsModifier.IN)
    val isReadonly: Boolean get() = hasModifier(TsModifier.READONLY)
    val isOut: Boolean get() = hasModifier(TsModifier.OUT)
    val isOverride: Boolean get() = hasModifier(TsModifier.OVERRIDE)
    val isDeclare: Boolean get() = hasModifier(TsModifier.DECLARE)

    fun hasModifier(modifier: TsModifier): Boolean
}

@JvmInline
value class TsModifiers(val mask: Int) : WithModifiers {
    companion object {
        val EMPTY = TsModifiers(0)
    }

    override fun hasModifier(modifier: TsModifier): Boolean = (mask and modifier.value) != 0
}
