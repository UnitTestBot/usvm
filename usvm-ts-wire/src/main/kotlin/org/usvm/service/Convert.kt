package org.usvm.service

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsScene
import manager.ArrayAccess as ProtoArrayAccess
import manager.CallExpr as ProtoCallExpr
import manager.Class as ProtoClass
import manager.ClassSignature as ProtoClassSignature
import manager.Field as ProtoField
import manager.FieldRef as ProtoFieldRef
import manager.FieldSignature as ProtoFieldSignature
import manager.File as ProtoFile
import manager.FileSignature as ProtoFileSignature
import manager.Method as ProtoMethod
import manager.MethodSignature as ProtoMethodSignature
import manager.Scene as ProtoScene

class ProtoToEtsConverter {
    // region model

    fun ProtoScene.toEts(): EtsScene {
        val files = files.map { it.toEts() }
        return EtsScene(files)
    }

    fun ProtoFile.toEts(): EtsFile {
        val signature = signature!!.toEts()
        val classes = classes.map { it.toEts() }
        val namespaces = emptyList<EtsNamespace>() // TODO
       return EtsFile(signature, classes, namespaces)
    }

    fun ProtoClass.toEts(): EtsClass {
        val signature = signature!!.toEts()
        val fields = fields.map { it.toEts() }
        val methods = methods.map { it.toEts() }
        return EtsClassImpl(
            signature = signature,
            fields = fields,
            methods = methods,
            // TODO: category, typeParameters, ...
        )
    }

    fun ProtoField.toEts(): EtsField {
        TODO()
    }

    fun ProtoMethod.toEts(): EtsMethod {
        TODO()
    }

    // endregion

    // region signatures

    fun ProtoFileSignature.toEts(): EtsFileSignature {
        TODO()
    }

    fun ProtoClassSignature.toEts(): EtsClassSignature {
        TODO()
    }

    fun ProtoFieldSignature.toEts(): EtsFieldSignature {
        TODO()
    }

    fun ProtoMethodSignature.toEts(): EtsMethodSignature {
        TODO()
    }

    // endregion

    // region values

    fun ProtoFieldRef.toEts(): EtsClass {
        TODO()
    }

    fun ProtoArrayAccess.toEts(): EtsClass {
        TODO()
    }

    fun ProtoCallExpr.toEts(): EtsClass {
        TODO()
    }

    // endregion
}
