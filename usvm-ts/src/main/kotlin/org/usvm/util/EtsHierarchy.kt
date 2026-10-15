package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
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
            .mapValues { (_, classes) ->
                classes
                    .groupBy { it.signature }
                    .mapValues { (_, declarations) -> canonicalClassDeclarations(declarations).single() }
                    .let { HashMap(it) }
            }
            .let { HashMap(it) }
    }

    private val ancestors: Map<EtsClass, Set<EtsClass>> by lazy {
        val result: Map<EtsClass, Set<EtsClass>>

        val time = measureTimeMillis {
            result = scene.projectAndSdkClasses.associateWith { start ->
                val visited = mutableSetOf(start)
                generateSequence(listOf(start)) { classes ->
                    if (classes.isEmpty()) return@generateSequence null

                    val result = classes.flatMap { current ->
                        val superClasses = current.superClass?.let { resolveClassesBySignature(it) }.orEmpty()
                        val interfaces = current.implementedInterfaces

                        val resolvedInterfaces = interfaces.flatMap { resolveClassesBySignature(it) }

                        superClasses.toMutableSet() + resolvedInterfaces // TODO optimize
                    }

                    val newAncestors = result.filter { visited.add(it) }
                    newAncestors.takeIf { it.isNotEmpty() }
                }.flatten().toMutableSet() + scene.sdkClasses.filter { it.name == "Object" }
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
        return ancestors[clazz] ?: run {
            error("TODO")
        }
    }

    /** Finds the closest instance declaration, preferring the class chain over interface declarations. */
    fun nearestInstanceMethod(clazz: EtsClass, methodName: String): EtsMethod? {
        val classes = ArrayDeque<EtsClass>()
        val interfaces = ArrayDeque<EtsClass>()
        val visited = mutableSetOf(clazz)
        classes.addLast(clazz)

        fun enqueue(target: ArrayDeque<EtsClass>, candidates: Collection<EtsClass>) {
            for (candidate in candidates.sortedBy { it.signature.toString() }) {
                if (visited.add(candidate)) target.addLast(candidate)
            }
        }

        while (classes.isNotEmpty()) {
            val current = classes.removeFirst()
            val method = current.methods.firstOrNull { !it.isStatic && it.name == methodName }
            if (method != null) return method

            val superClasses = current.superClass?.let(::resolveClassesBySignature).orEmpty()
            enqueue(classes, superClasses)

            val implementedInterfaces = current.implementedInterfaces.flatMap(::resolveClassesBySignature)
            enqueue(interfaces, implementedInterfaces)
        }

        val objectMethod = scene.sdkClasses.asSequence()
            .filter { it.name == "Object" }
            .sortedBy { it.signature.toString() }
            .mapNotNull { sdkObject ->
                sdkObject.methods.firstOrNull { !it.isStatic && it.name == methodName }
            }
            .firstOrNull()
        if (objectMethod != null) return objectMethod

        while (interfaces.isNotEmpty()) {
            val current = interfaces.removeFirst()
            val method = current.methods.firstOrNull { !it.isStatic && it.name == methodName }
            if (method != null) return method

            val parents = current.implementedInterfaces.flatMap(::resolveClassesBySignature)
            enqueue(interfaces, parents)
        }

        return null
    }

    /** Explicit `super.m()` starts lookup at the declared superclass, not the receiver's override. */
    fun explicitSuperMethod(receiverClass: EtsClass, callee: EtsMethodSignature): EtsMethod? {
        val ancestors = getAncestors(receiverClass) - receiverClass
        val declaredSuperclasses = classesForType(EtsClassType(callee.enclosingClass))

        return declaredSuperclasses.asSequence()
            .filter(ancestors::contains)
            .sortedBy { it.signature.toString() }
            .mapNotNull { nearestInstanceMethod(it, callee.name) }
            .firstOrNull()
    }

    fun getInheritors(clazz: EtsClass): Set<EtsClass> {
        return inheritors[clazz] ?: run {
            error("TODO")
        }
    }

    fun classesForType(etsClassType: EtsRefType): Collection<EtsClass> {
        if (etsClassType is EtsArrayType) {
            return scene.sdkClasses.filter { it.name == "Array" }
        }

        require(etsClassType is EtsClassType || etsClassType is EtsUnclearRefType) {
            "Expected EtsClassType or EtsUnclearRefType, but got ${etsClassType::class.simpleName}"
        }

        // We don't want to remove names like "$AC2$FieldAccess.createObject"
        val typeName = etsClassType.typeName.removeTrashFromTheName()
        val suitableClasses = resolveMap[typeName] ?: return emptySet()

        if (etsClassType.isResolved()) {
            val signature = (etsClassType as EtsClassType).signature.copy(name = typeName)
            return suitableClasses[signature]?.let { setOf(it) } ?: emptySet()
        }

        return suitableClasses.values
    }

    companion object {
        private val objectFile = EtsFileSignature(projectName = "", fileName = "lib.es5.d.ts")

        // The Object declaration in the TypeScript library bundled by JacoDB.
        val OBJECT_CLASS = EtsClassType(
            signature = EtsClassSignature(
                name = "Object",
                file = objectFile
            ),
        )
    }
}

/** Class/interface declaration merging augments a type without adding another runtime class. */
internal fun canonicalClassDeclarations(declarations: List<EtsClass>): List<EtsClass> {
    val runtimeClass = declarations.singleOrNull { it.category == EtsClassCategory.CLASS }
    val hasOnlyInterfaceAugmentations = declarations.all {
        it === runtimeClass || it.category == EtsClassCategory.INTERFACE
    }
    val isClassInterfaceMerge = runtimeClass != null && hasOnlyInterfaceAugmentations
    if (isClassInterfaceMerge) return listOf(requireNotNull(runtimeClass))

    return declarations
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
