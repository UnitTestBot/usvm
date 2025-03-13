package org.usvm.model

interface Base : WithModifiers {
    val modifiers: TsModifiers
    val decorators: List<TsDecorator>

    // In TS, if "public" modifier is not specified,
    // an entity considered public if it is not private and not protected.
    override val isPublic: Boolean
        get() = super.isPublic || (!isPrivate && !isProtected)

    override fun hasModifier(modifier: TsModifier): Boolean = modifiers.hasModifier(modifier)

    fun hasDecorator(decorator: TsDecorator): Boolean = decorators.contains(decorator)
}
