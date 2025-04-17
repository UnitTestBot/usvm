package org.usvm.util

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.machine.TsContext

fun TsContext.resolveEtsField(
    instance: EtsLocal?,
    field: EtsFieldSignature,
): EtsField {
    // Perfect signature:
    if (field.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val clazz = scene.projectAndSdkClasses.single { cls ->
            cls.name == field.enclosingClass.name
        }
        val fields = clazz.fields.filter { it.name == field.name }
        if (fields.size == 1) {
            return fields.single()
        }
    }

    // Unknown signature:
    if (instance != null) {
        val instanceType = instance.type
        when (instanceType) {
            is EtsClassType -> {
                val field = tryGetSingleField(scene, instanceType.signature.name, field.name)
                if (field != null) return field
            }

            is EtsUnclearRefType -> {
                val field = tryGetSingleField(scene, instanceType.typeName, field.name)
                if (field != null) return field
            }
        }
    }

    val fields = scene.projectAndSdkClasses.flatMap { cls ->
        cls.fields.filter { it.name == field.name }
    }
    if (fields.size == 1) {
        return fields.single()
    }
    error("Cannot resolve field $field")
}

private fun tryGetSingleField(
    scene: EtsScene,
    className: String,
    fieldName: String,
): EtsField? {
    val classes = scene.projectAndSdkClasses.filter { cls ->
        cls.name == className
    }
    if (classes.size == 1) {
        val clazz = classes.single()
        return clazz.fields.single { it.name == fieldName }
    }
    val fields = classes.flatMap { cls ->
        cls.fields.filter { it.name == fieldName }
    }
    if (fields.size == 1) {
        return fields.single()
    }
    return null
}
