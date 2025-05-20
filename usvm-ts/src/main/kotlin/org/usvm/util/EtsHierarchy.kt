package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType

private val logger = KotlinLogging.logger { }
private typealias ClassName = String

class EtsHierarchy(private val scene: EtsScene) {
    private val resolveMap: Map<ClassName, Map<EtsClassSignature, EtsClass>> by lazy {
        scene.projectAndSdkClasses
            .groupBy { it.name }
            .mapValues { (_, classes) ->
                classes
                    .groupBy { it.signature }
                    .mapValues { it.value.single() }
            }
    }

    private val directInheritors: Map<EtsClass, Set<EtsClass>> by lazy {
        scene.projectAndSdkClasses.associateWith { current ->
            val superClassSignature = current.superClass ?: return@associateWith emptySet()

            val className = superClassSignature.name.nameWithoutGenerics().removePrefixWithDots()
            val classesWithTheSameName = resolveMap[className] ?: run {
                logger.error("No class $className found in the scene")
                return@associateWith emptySet()
            }
            val classesWithTheSameSignature = classesWithTheSameName[superClassSignature]
            val superClasses = when {
                classesWithTheSameSignature != null -> listOf(classesWithTheSameSignature)
                superClassSignature.file == EtsFileSignature.UNKNOWN -> classesWithTheSameName.values
                else -> error("There is no class with name ${superClassSignature.name}")
            }

            val interfaces = current.implementedInterfaces
            val resolvedInterfaces = interfaces.flatMap {
                val interfaceName = it.name.nameWithoutGenerics().removePrefixWithDots()
                val resolvedInterface = resolveMap[interfaceName] ?: run {
                    logger.error("No class $interfaceName found in the scene")
                    return@associateWith emptySet()
                }
                val interfacesWithTheSameSignature = resolvedInterface[it]
                when {
                    interfacesWithTheSameSignature != null -> listOf(interfacesWithTheSameSignature)
                    it.file == EtsFileSignature.UNKNOWN -> resolvedInterface.values
                    else -> error("There is no class with name ${it.name}")
                }
            }

            superClasses.toHashSet() + resolvedInterfaces
        }
    }

    private val ancestors: Map<EtsClass, Set<EtsClass>> by lazy {
        scene.projectAndSdkClasses.associateWith { start ->
            generateSequence(listOf(start)) { classes ->
                classes.flatMap {
                    directInheritors[it] ?: return@generateSequence null
                }
            }.flatten().toSet()
        }
    }

    private val inheritors: MutableMap<EtsClass, MutableSet<EtsClass>> by lazy {
        val result = hashMapOf<EtsClass, MutableSet<EtsClass>>()
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

    fun classesForType(etsClassType: EtsRefType): Collection<EtsClass> {
        require(etsClassType is EtsClassType || etsClassType is EtsUnclearRefType)

        val typeName = etsClassType.typeName.removeTrashFromTheName()
        val suitableClasses = resolveMap[typeName] ?: return emptySet()

        if (etsClassType.isResolved()) {
            val signature = (etsClassType as EtsClassType).signature
            return suitableClasses[signature]?.let { hashSetOf(it) } ?: emptySet()
        }

        return suitableClasses.values
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

fun ClassName.nameWithoutGenerics(): ClassName {
    return substringBefore('<')
}

fun ClassName.removePrefixWithDots(): ClassName {
    return substringAfterLast('.')
}

fun ClassName.removeTrashFromTheName(): ClassName {
    return nameWithoutGenerics().removePrefixWithDots()
}