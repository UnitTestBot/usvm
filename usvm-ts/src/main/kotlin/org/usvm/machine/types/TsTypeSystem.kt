package org.usvm.machine.types

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsNumberType
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
    val project: EtsScene,
    val hierarchy: EtsHierarchy,
    ) : UTypeSystem<EtsType> {

    companion object {
        // TODO: add more primitive types (string, etc.) once supported
        val primitiveTypes = sequenceOf(EtsNumberType, EtsBooleanType)
    }

    /**
     * @return true if [type] <: [supertype].
     */
    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean {
        return when {
            type is AuxiliaryType -> {
                if (supertype is EtsUnknownType || supertype is EtsAnyType) return true

                if (supertype is AuxiliaryType) {
                    return type.properties.all { it in supertype.properties }
                }
                val supertypeClass = project.projectAndSdkClasses.single { it.type.typeName == supertype.typeName }
                val supertypeFields = supertypeClass.getAllFields(hierarchy)
                type.properties.all { it in supertypeFields.map { it.name } }
            }

            supertype is AuxiliaryType -> {
                if (type is EtsUnknownType || type is EtsAnyType) return supertype.properties.isEmpty()

                val clazz = project.projectAndSdkClasses.single { it.type.typeName == type.typeName }
                val typeFields = clazz.getAllFields(hierarchy)
                typeFields.mapTo(mutableSetOf()) { it.name }.containsAll(supertype.properties)
            }
            supertype == type -> true
            supertype == EtsUnknownType || supertype == EtsAnyType -> true
            supertype is EtsPrimitiveType || type is EtsPrimitiveType -> type == supertype
            else -> {
                // TODO isAssignable
                if (supertype is EtsUnknownType || supertype is EtsAnyType) {
                    return true
                }

                if (type is EtsUnknownType || type is EtsAnyType) {
                    return false // otherwise it should be processed in the code above
                }

                // TODO wrong type resolutions because of names
                val clazz =
                    project.projectAndSdkClasses.singleOrNull() { it.type.typeName == type.typeName } ?: error("TODO")
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
        is EtsPrimitiveType -> types.isEmpty()
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
        if (type is EtsUnknownType) return false
        if (type is EtsAnyType) return false

        return true
    }

    /**
     * @return a sequence of **direct** inheritors of the [type].
     */
    override fun findSubtypes(type: EtsType): Sequence<EtsType> = when (type) {
        is EtsPrimitiveType -> emptySequence() // TODO why???
        // TODO they should be direct inheritors, not all of them
        is EtsAnyType,
        is EtsUnknownType -> project.projectAndSdkClasses.asSequence().map { it.type }

        is AuxiliaryType -> {
            project.projectAndSdkClasses.filter {
                it.getAllFields(hierarchy).mapTo(mutableSetOf()) { it.name }.containsAll(type.properties)
            }.asSequence().map { it.type } // TODO get fields of ancestors
        }

        else -> {
            val clazz = project.projectAndSdkClasses.filter { it.type == type }
            // TODO optimize
            project.projectAndSdkClasses.asSequence().filter { it.superClass == clazz }.map { it.type }
        }
    }

    private val topTypeStream by lazy { USupportTypeStream.from(this, EtsUnknownType) }

    override fun topTypeStream(): UTypeStream<EtsType> = topTypeStream
}

