package org.usvm.util

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.machine.TsContext

fun TsContext.resolveEtsMethods(
    method: EtsMethodSignature,
    instanceType: EtsType = EtsUnknownType,
): List<EtsMethod> {
    if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = scene.projectAndSdkClasses.filter {
            it.name == method.enclosingClass.name
        }
        val methods = classes.flatMap {
            it.methods
        }.filter {
            it.name == method.name
        }
        return methods
    }

    val classes = if (instanceType is EtsClassType) {
        scene.projectAndSdkClasses.filter {
            it.name == instanceType.signature.name
        }
    } else {
        scene.projectAndSdkClasses
    }
    val methods = classes.flatMap {
        it.methods
    }.filter {
        it.name == method.name
    }
    return methods
}
