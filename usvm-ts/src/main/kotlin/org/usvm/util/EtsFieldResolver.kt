package org.usvm.util

import org.jacodb.ets.model.EtsClass
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
    hierarchy: EtsHierarchy,
): EtsFieldResolutionResult {
    // Perfect signature:
    if (field.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = scene.projectAndSdkClasses.filter { cls ->
            cls.name == field.enclosingClass.name
        }
        if (classes.isEmpty()) {
            error("Cannot resolve class ${field.enclosingClass.name}")
        }
        if (classes.size > 1) {
            error("Multiple classes with name ${field.enclosingClass.name}")
        }
        val clazz = classes.single()
        val fields = clazz.getAllFields(hierarchy).filter { it.name == field.name }
        if (fields.size == 1) {
            return EtsFieldResolutionResult.create(fields.single())
        }
    }

    // Unknown signature:
    if (instance != null) {
        val instanceType = instance.type
        when (instanceType) {
            is EtsClassType -> {
                val field = tryGetSingleField(scene, instanceType.signature.name, field.name, hierarchy)
                if (field != null) return EtsFieldResolutionResult.create(field)
            }

            is EtsUnclearRefType -> {
                val field = tryGetSingleField(scene, instanceType.typeName, field.name, hierarchy)
                if (field != null) return EtsFieldResolutionResult.create(field)
            }
        }
    }

    val fields = scene.projectAndSdkClasses.flatMap { cls ->
        cls.getAllFields(hierarchy).filter { it.name == field.name }
    }

    return EtsFieldResolutionResult.create(fields)
}

private fun tryGetSingleField(
    scene: EtsScene,
    className: String,
    fieldName: String,
    hierarchy: EtsHierarchy,
): EtsField? {
    val classes = scene.projectAndSdkClasses.filter { cls ->
        cls.name == className
    }
    if (classes.size == 1) {
        val clazz = classes.single()
        val fields = clazz.getAllFields(hierarchy).filter { it.name == fieldName }
        if (fields.isEmpty()) {
            error("No field with name '$fieldName' in class '${clazz.name}'")
        }
        if (fields.size > 1) {
            error("Multiple fields with name '$fieldName' in class '${clazz.name}': $fields")
        }
        return fields.single()
    }
    val fields = classes.flatMap { cls ->
        cls.fields.filter { it.name == fieldName }
    }
    if (fields.size == 1) {
        return fields.single()
    }
    return null
}

fun EtsClass.getAllFields(hierarchy: EtsHierarchy): List<EtsField> {
    return hierarchy.getAncestor(this).flatMap { it.fields }
}

sealed class EtsFieldResolutionResult {
    data class Unique(val field: EtsField) : EtsFieldResolutionResult()
    data class Ambiguous(val fields: List<EtsField>) : EtsFieldResolutionResult()
    data object Empty : EtsFieldResolutionResult()

    companion object {
        fun create(field: EtsField) = Unique(field)

        fun create(fields: List<EtsField>): EtsFieldResolutionResult {
            return when {
                fields.isEmpty() -> Empty
                fields.size == 1 -> Unique(fields.single())
                else -> Ambiguous(fields)
            }
        }
    }
}