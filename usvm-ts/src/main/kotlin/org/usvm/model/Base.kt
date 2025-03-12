package org.usvm.model

interface Base : WithModifiers {
    val modifiers: TsModifiers
    val decorators: List<TsDecorator>

    // If not specified, entity is public if not private and not protected
    override val isPublic: Boolean
        get() = super.isPublic || (!isPrivate && !isProtected)

    override fun hasModifier(modifier: TsModifier): Boolean = modifiers.hasModifier(modifier)

    fun hasDecorator(decorator: TsDecorator): Boolean = decorators.contains(decorator)
}
