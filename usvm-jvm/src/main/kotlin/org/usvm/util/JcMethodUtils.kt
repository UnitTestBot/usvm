package org.usvm.util

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.toType

/**
 * Checks if the method can be overridden:
 * - it isn't static;
 * - it isn't a constructor;
 * - it isn't final;
 * - it isn't private;
 * - its enclosing class isn't final.
 */
fun JcMethod.canBeOverridden(): Boolean =
    /*
        https://stackoverflow.com/a/30416883
     */
    !isStatic && !isConstructor && !isFinal && !isPrivate && !enclosingClass.isFinal


fun JcType.findMethod(method: JcMethod): JcTypedMethod? = when (this) {
    is JcClassType -> findClassMethod(method.name, method.description)
    // Array types are objects and have methods of java.lang.Object
    is JcArrayType -> jcClass.toType().findClassMethod(method.name, method.description)
    else -> error("Unexpected type: $this")
}

private fun JcClassType.findClassMethod(name: String, desc: String): JcTypedMethod? {
    val method = findMethodOrNull { it.name == name && it.method.description == desc }
    if (method != null) return method

    /**
     * Method implementation was not found in current class but class is instantiatable.
     * Therefore, method implementation is provided by the super class.
     * */
    val superClass = superType
    if (superClass != null) {
        return superClass.findClassMethod(name, desc)
    }

    return null
}
