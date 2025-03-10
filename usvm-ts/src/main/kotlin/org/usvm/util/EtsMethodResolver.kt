package org.usvm.util

import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.UNKNOWN_CLASS_NAME
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.machine.TsContext

fun TsContext.resolveEtsMethods(
    instance: EtsLocal,
    method: EtsMethodSignature,
): List<EtsMethod> {
    if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = scene.projectAndSdkClasses.filter {
            it.name == method.enclosingClass.name
        }
        val methods = classes.flatMap {
            it.methods + it.ctor
        }.filter {
            it.name == method.name
        }
        return methods
    }

    val instanceType = instance.type
    val classes = if (instanceType is EtsClassType) {
        scene.projectAndSdkClasses.filter {
            it.name == instanceType.signature.name
        }
    } else {
        scene.projectAndSdkClasses.filter {
            it.name == method.enclosingClass.name
        }
    }
    val methods = classes.flatMap {
        it.methods + it.ctor
    }.filter {
        it.name == method.name
    }
    return methods
}
