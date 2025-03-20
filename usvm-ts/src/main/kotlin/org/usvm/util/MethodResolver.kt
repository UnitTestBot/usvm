package org.usvm.util

import org.jacodb.ets.base.UNKNOWN_CLASS_NAME
import org.usvm.machine.TsContext
import org.usvm.model.TsClassType
import org.usvm.model.TsLocal
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsType
import org.usvm.model.TsUnknownType

fun TsContext.resolveTsMethods(
    method: TsMethodSignature,
    instanceType: TsType = TsUnknownType,
): List<TsMethod> {
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

    val classes = if (instanceType is TsClassType) {
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
