package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger { }
private typealias ClassName = String

class EtsHierarchy(private val scene: EtsScene) {
    private val resolveMap: Map<ClassName, Map<EtsClassSignature, EtsClass>> by lazy {
        scene.projectAndSdkClasses
            .groupBy { it.name }
            .mapValuesTo(hashMapOf()) { (_, classes) ->
                classes
                    .groupBy { it.signature }
                    .mapValuesTo(hashMapOf()) { it.value.single() }
            }
    }

    private val ancestors: Map<EtsClass, Set<EtsClass>> by lazy {
        val result: Map<EtsClass, Set<EtsClass>>

        val time = measureTimeMillis {
            result = scene.projectAndSdkClasses.associateWith { start ->
                generateSequence(listOf(start)) { classes ->
                    if (classes.isEmpty()) return@generateSequence null

                    val result = classes.flatMap { current ->
                        val superClassSignature = current.superClass ?: return@generateSequence null

                        val superClasses = resolveClassesBySignature(superClassSignature)
                        val interfaces = current.implementedInterfaces

                        val resolvedInterfaces = interfaces.flatMap { resolveClassesBySignature(it) }

                        superClasses.toMutableSet() + resolvedInterfaces // TODO optimize
                    }

                    result.takeIf { it.isNotEmpty() }
                }.flatten().toMutableSet() + classesForType(OBJECT_CLASS_TYPE)
            }
        }

        if (time > 100) {
            logger.warn { "Ancestors map is built in $time ms" }
        }

        return@lazy result
    }

    private fun resolveClassesBySignature(superClassSignature: EtsClassSignature): Collection<EtsClass> {
        val typeName = superClassSignature.name.removeTrashFromTheName()
        val signature = superClassSignature.copy(name = typeName)

        val classesWithTheSameName = resolveMap[typeName] ?: run {
            // logger.error("No class with $superClassSignature found in the Scene")
            return emptyList()
        }

        val classesWithTheSameSignature = classesWithTheSameName[signature]
        val superClasses = when {
            classesWithTheSameSignature != null -> listOf(classesWithTheSameSignature)
            superClassSignature.file == EtsFileSignature.UNKNOWN -> classesWithTheSameName.values
            else -> error("There is no class with name ${superClassSignature.name}")
        }
        return superClasses
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

    fun getAncestors(clazz: EtsClass): Set<EtsClass> {
        return ancestors[clazz].orEmpty()
    }

    fun getInheritors(clazz: EtsClass): Set<EtsClass> {
        return inheritors[clazz].orEmpty()
    }

    fun classesForType(type: EtsRefType): Collection<EtsClass> {
        if (type is EtsArrayType) {
            return scene.sdkClasses.filter { it.name == "Array" }
        }

        if (type.typeName.equals("object", ignoreCase = true) && type !== OBJECT_CLASS_TYPE) {
            return listOf(objectClass)
        }

        require(type is EtsClassType || type is EtsUnclearRefType) {
            "Expected EtsClassType or EtsUnclearRefType, but got ${type::class.simpleName}"
        }

        // We don't want to remove names like "$AC2$FieldAccess.createObject"
        val typeName = type.typeName.removeTrashFromTheName()
        val suitableClasses = resolveMap[typeName] ?: return emptySet()

        if (type.isResolved()) {
            val signature = (type as EtsClassType).signature.copy(name = typeName)
            return suitableClasses[signature]?.let { setOf(it) } ?: emptySet()
        }

        return suitableClasses.values
    }

    val objectClass: EtsClass by lazy {
        val map = resolveMap["Object"].orEmpty()
        val sig = OBJECT_CLASS_TYPE.signature
        map[sig] ?: EtsClassImpl(
            signature = sig,
            fields = emptyList(),
            methods = emptyList(),
        )
    }

    companion object {
        // TODO use real one
        val OBJECT_CLASS_TYPE = EtsClassType(
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
    // return substringAfterLast('.')
    return this
}

fun ClassName.removeTrashFromTheName(): ClassName {
    return if (this.startsWith("%AC")) {
        this
    } else {
        nameWithoutGenerics().removePrefixWithDots()
    }
}
