package org.usvm.util

import org.jacodb.api.JcMethod

/**
 * Checks if the method is the same as its definition (i.e. it is false for
 * subclasses' methods which use base definition).
 */
fun JcMethod.isDefinition(): Boolean {
    if (instList.size == 0) {
        return true
    }
    return instList.first().location.method == this
}

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
