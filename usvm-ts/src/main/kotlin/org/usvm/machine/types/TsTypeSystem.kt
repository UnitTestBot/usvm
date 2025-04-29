package org.usvm.machine.types

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.util.EtsHierarchy
import org.usvm.util.getAllFields
import org.usvm.util.type
import kotlin.time.Duration

// TODO this is draft, should be replaced with real implementation
class TsTypeSystem(
    val scene: EtsScene,
    override val typeOperationsTimeout: Duration,
    val hierarchy: EtsHierarchy,
    ) : UTypeSystem<EtsType> {
    /**
     * @return true if [type] <: [supertype].
     */
    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean {
        return when {
            type is AuxiliaryType -> {
                if (supertype is EtsUnknownType || supertype is EtsAnyType) {
                    return true
                }

                if (supertype == EtsHierarchy.OBJECT_CLASS) {
                    return true
                }

                if (supertype is AuxiliaryType) {
                    return type.properties.all { it in supertype.properties }
                }

                val supertypeClass = scene.projectAndSdkClasses.single { it.type.typeName == supertype.typeName }
                val supertypeFields = supertypeClass.getAllFields(hierarchy)
                type.properties.all { it in supertypeFields.map { it.name } }
            }

            supertype is AuxiliaryType -> {
                if (type is EtsUnknownType || type is EtsAnyType) {
                    return supertype.properties.isEmpty()
                }

                if (type == EtsHierarchy.OBJECT_CLASS) {
                    return supertype.properties.isEmpty()
                }

                val clazz = scene.projectAndSdkClasses.single { it.type.typeName == type.typeName }
                val typeFields = clazz.getAllFields(hierarchy)
                typeFields.mapTo(mutableSetOf()) { it.name }.containsAll(supertype.properties)
            }

            supertype == type -> true
            supertype == EtsUnknownType || supertype == EtsAnyType -> {
                true
            }

            supertype is EtsPrimitiveType || type is EtsPrimitiveType -> {
                type == supertype
            }

            else -> {
                // TODO isAssignable
                if (supertype is EtsUnknownType || supertype is EtsAnyType) {
                    return true
                }

                if (type is EtsUnknownType || type is EtsAnyType) {
                    return false // otherwise it should be processed in the code above
                }

                if (type == EtsHierarchy.OBJECT_CLASS) {
                    return supertype == EtsHierarchy.OBJECT_CLASS
                }

                if (supertype == EtsHierarchy.OBJECT_CLASS) {
                    return true // TODO not primitive
                }

                // TODO wrong type resolutions because of names
                val clazz = scene
                    .projectAndSdkClasses
                    .singleOrNull() { it.type.typeName == type.typeName }
                    ?: error("TODO")
                val ancestors = hierarchy.getAncestor(clazz).map { it.type }

                if (supertype is EtsClassType) {
                    return ancestors.any { it == supertype }
                }

                if (supertype is EtsUnclearRefType) {
                    return ancestors.any { it.typeName == supertype.typeName }
                }

                error("TODO")
            }
        }
    }

    /**
     * @return true if [types] and [type] can be supertypes for some type together.
     * It is guaranteed that [type] is not a supertype for any type from [types]
     * and that [types] have common subtype.
     */
    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean = when (type) {
        is AuxiliaryType -> true
        is EtsPrimitiveType -> {
            types.isEmpty()
        }
        is EtsClassType -> true
        is EtsUnclearRefType -> true
        is EtsArrayType -> TODO()
        else -> error("Unsupported class type: $type")
    }

    // TODO is it right?
    /**
     * @return true if there is no type u distinct from [type] and subtyping [type].
     */
    override fun isFinal(type: EtsType): Boolean = type is EtsPrimitiveType

    // TODO are there any non instantiable types?
    /**
     * @return true if [type] is instantiable, meaning it can be created via constructor.
     */
    override fun isInstantiable(type: EtsType): Boolean {
        if (type is EtsUnknownType) {
            return false
        }
        if (type is EtsAnyType) {
            return false
        }

        return true
    }

    /**
     * @return a sequence of **direct** inheritors of the [type].
     */
    override fun findSubtypes(type: EtsType): Sequence<EtsType> {
        return when (type) {
            is EtsPrimitiveType -> emptySequence() // TODO why???
            // TODO they should be direct inheritors, not all of them
            is EtsAnyType,
            is EtsUnknownType -> {
                // scene.projectAndSdkClasses.asSequence().map { it.type }
                error("")
            }

            is AuxiliaryType -> {
                scene.projectAndSdkClasses.filter {
                    it.getAllFields(hierarchy).mapTo(mutableSetOf()) { it.name }.containsAll(type.properties)
                }.asSequence().map { it.type } // TODO get fields of ancestors
            }

            else -> {
                if (type == EtsHierarchy.OBJECT_CLASS) {
                    return project.projectAndSdkClasses.asSequence().map { it.type }
                }
                // TODO wrong usage of names
                if (type is EtsUnclearRefType) {
                    val classes = scene.projectAndSdkClasses.filter { it.type.typeName == type.typeName }
                    classes.asSequence().flatMap { hierarchy.getInheritors(it) }.map { it.type }
                } else {
                    val clazz = scene.projectAndSdkClasses.singleOrNull { it.type == type } ?: error("Cannot find class for $type")
                    hierarchy.getInheritors(clazz).asSequence().map { it.type }
                }
            }
        }
    }

    private val topTypeStream by lazy { USupportTypeStream.from(this, EtsHierarchy.OBJECT_CLASS) }

    override fun topTypeStream(): UTypeStream<EtsType> = topTypeStream
}
