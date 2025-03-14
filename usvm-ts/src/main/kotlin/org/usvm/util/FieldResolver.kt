package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.base.UNKNOWN_CLASS_NAME
import org.usvm.machine.TsContext
import org.usvm.model.*

private val logger = KotlinLogging.logger {}

fun TsContext.resolveTsField(
    instance: TsLocal?,
    field: TsFieldSignature,
): TsField {
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
        val instanceType = TsUnknownType // TODO: instance.type
        when (instanceType) {
            is TsClassType -> {
                val field = tryGetSingleField(scene, instanceType.signature.name, field.name)
                if (field != null) return field
            }

            is TsUnclearType -> {
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
    scene: TsScene,
    className: String,
    fieldName: String,
): TsField? {
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

fun TsContext.resolveTsFields(
    instance: TsLocal?,
    field: TsFieldSignature,
): List<TsField> {
    // Perfect signature:
    if (field.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = scene.projectAndSdkClasses.filter { cls ->
            cls.name == field.enclosingClass.name
        }
        if (classes.size == 1) {
            val clazz = classes.single()
            val fields = clazz.fields.filter { it.name == field.name }
            if (fields.size == 1) {
                return listOf(fields.single())
            }
        } else {
            logger.warn { "Multiple classes with name ${field.enclosingClass.name}" }
        }
    }

    // Unknown signature:
    if (instance != null) {
        val instanceType = TsUnknownType // TODO: instance.type
        when (instanceType) {
            is TsClassType -> {
                val field = tryGetSingleField(scene, instanceType.signature.name, field.name)
                if (field != null) return listOf(field)
            }

            is TsUnclearType -> {
                val field = tryGetSingleField(scene, instanceType.typeName, field.name)
                if (field != null) return listOf(field)
            }
        }
    }

    val fields = scene.projectAndSdkClasses.flatMap { cls ->
        cls.fields.filter { it.name == field.name }
    }
    return fields
}
