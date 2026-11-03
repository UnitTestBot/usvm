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
    // Source-model helpers need their exact private owner. Name-based application lookup must never discover them.
    val resolutionClasses = scene.projectAndSdkClasses.filter { owner ->
        !isSourceModelClass(owner.signature) || owner.signature == method.enclosingClass
    }

    if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = resolutionClasses.filter {
            it.name == method.enclosingClass.name
        }
        val methods = classes.flatMap {
            it.methods
        }.filter {
            it.name == method.name && it.signature.wellKnownSymbol == method.wellKnownSymbol
        }
        return methods
    }

    val classes = if (instanceType is EtsClassType) {
        resolutionClasses.filter {
            it.name == instanceType.signature.name
        }
    } else {
        resolutionClasses
    }
    val methods = classes.flatMap {
        it.methods
    }.filter {
        it.name == method.name && it.signature.wellKnownSymbol == method.wellKnownSymbol
    }
    return methods
}
