package org.usvm.util

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import kotlin.collections.flatten

private typealias ClassName = String

class EtsHierarchy(private val scene: EtsScene) {
    private val resolveMap: Map<ClassName, Map<EtsClassSignature, EtsClass>> by lazy {
        scene.projectAndSdkClasses
            .groupBy { it.name }
            .mapValues { (_, classes) ->
                classes
                    .groupBy { it.signature }
                    .map { it.key to it.value.single() }
                    .toMap()
            }
    }

    private val ancestors: Map<EtsClass, Set<EtsClass>> by lazy {
        val classes = scene.projectAndSdkClasses
        val ancestors = classes.map {
            it to generateSequence(listOf(it)) { currentClasses ->
                currentClasses.flatMap { currentClass ->
                    val superClassSignature = currentClass.superClass ?: return@generateSequence null
                    val classesWithTheSameName = resolveMap.getValue(superClassSignature.name)
                    val classesWithTheSameSignature = classesWithTheSameName[superClassSignature]
                    val superClasses = when {
                        classesWithTheSameSignature != null -> listOf(classesWithTheSameSignature)
                        superClassSignature.file == EtsFileSignature.UNKNOWN -> classesWithTheSameName.values
                        else -> error("There is no class with name ${superClassSignature.name}")
                    }
                    val interfaces = currentClass.implementedInterfaces
                    require(interfaces.isEmpty()) { TODO() }
                    val resolvedInterfaces = interfaces.map { interfaceSignature ->
                        resolveMap[currentClass.name]?.get(interfaceSignature) ?: error("Unresolved interface")
                    }
                    superClasses + resolvedInterfaces
                }
            }.flatten().toSet()
        }
        ancestors.toMap()
    }

    private val inheritors: MutableMap<EtsClass, MutableSet<EtsClass>> by lazy {
        val result = mutableMapOf<EtsClass, MutableSet<EtsClass>>()
        ancestors.forEach { (key, value) ->
            value.forEach { clazz ->
                result.getOrPut(clazz) { hashSetOf() }.add(key)
            }
        }
        result
    }

    fun getAncestor(clazz: EtsClass): Set<EtsClass> {
        return ancestors[clazz] ?: run {
            error("TODO")
        }
    }

    fun getInheritors(clazz: EtsClass): Set<EtsClass> {
        return inheritors[clazz] ?: run {
            error("TODO")
        }
    }

    companion object {
        // TODO use real one
        val OBJECT_CLASS = EtsClassType(
            signature = EtsClassSignature(
                name = "Object",
                file = EtsFileSignature(projectName = "ES2015", fileName = "BuiltinClass")
            ),
        )
    }
}