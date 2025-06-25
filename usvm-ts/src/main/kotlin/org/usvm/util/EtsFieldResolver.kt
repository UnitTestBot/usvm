package org.usvm.util

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.machine.TsContext

fun TsContext.resolveEtsField(
    instance: EtsLocal?,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): EtsPropertyResolution<out EtsField> {
    // Perfect signature:
    if (field.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = hierarchy.classesForType(EtsClassType(field.enclosingClass))
        if (classes.isEmpty()) {
            error("Cannot resolve class ${field.enclosingClass.name}")
        }
        if (classes.size > 1) {
            error("Multiple classes with name ${field.enclosingClass.name}")
        }
        val clazz = classes.single()
        val fields = clazz.getAllFields(hierarchy).filter { it.name == field.name }
        if (fields.size == 1) {
            return EtsPropertyResolution.create(fields.single())
        }
    }

    // Unknown signature:
    if (instance != null) {
        val instanceType = instance.type
        when (instanceType) {
            is EtsClassType -> {
                val field = tryGetSingleField(scene, instanceType.signature.name, field.name, hierarchy)
                if (field != null) return EtsPropertyResolution.create(field)
            }

            is EtsUnclearRefType -> {
                val field = tryGetSingleField(scene, instanceType.typeName, field.name, hierarchy)
                if (field != null) return EtsPropertyResolution.create(field)
            }
        }
    }

    val fields = scene.projectAndSdkClasses.flatMap { cls ->
        cls.getAllFields(hierarchy).filter { it.name == field.name }
    }

    return EtsPropertyResolution.create(fields)
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

fun EtsClass.getAllMethods(hierarchy: EtsHierarchy): List<EtsMethod> {
    return hierarchy.getAncestor(this).flatMap { it.methods }
}

fun EtsClass.getAllPropertiesCombined(hierarchy: EtsHierarchy): Set<String> {
    val (fields, methods) = getAllProperties(hierarchy)
    return fields + methods
}

fun EtsClass.getAllProperties(hierarchy: EtsHierarchy): Pair<Set<EtsFieldName>, Set<EtsMethodName>> {
    val allFields = hashSetOf<EtsFieldName>()
    val allMethods = hashSetOf<EtsMethodName>()

    val classes = hierarchy.getAncestor(this)

    classes.forEach {
        val fieldNames = it.fields.map<EtsField, EtsFieldName> { it.name }
        allFields.addAll(fieldNames)

        val methods = it.methods.filter { it.name != CONSTRUCTOR_NAME }
        val methodNames = methods.map<EtsMethod, EtsFieldName> { it.name }
        allMethods.addAll(methodNames)
    }

    return allFields to allMethods
}

sealed class EtsPropertyResolution<T> {
    data class Unique<T>(val property: T) : EtsPropertyResolution<T>()
    data class Ambiguous<T>(val properties: List<T>) : EtsPropertyResolution<T>()
    data object Empty : EtsPropertyResolution<Nothing>()

    companion object {
        fun <T> create(property: T) = Unique(property)

        fun <T> create(properties: List<T>): EtsPropertyResolution<out T> {
            return when {
                properties.isEmpty() -> Empty
                properties.size == 1 -> Unique(properties.single())
                else -> Ambiguous(properties)
            }
        }
    }
}

typealias EtsMethodName = String
typealias EtsFieldName = String
