package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaObjectType
import org.jacodb.panda.dynamic.api.PandaPrimitiveType
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream
import kotlin.time.Duration

class PandaTypeSystem(override val typeOperationsTimeout: Duration, val project: PandaProject) : UTypeSystem<PandaType> {
    override fun isSupertype(supertype: PandaType, type: PandaType): Boolean {
        if (supertype == type) return true

        if (type is PandaPrimitiveType) return false

        // TODO
        return false
    }

    override fun hasCommonSubtype(type: PandaType, types: Collection<PandaType>): Boolean {
        if (type is PandaPrimitiveType) return false

        return false // TODO
    }

    override fun isFinal(type: PandaType): Boolean {
        if (type is PandaPrimitiveType) return true

        // TODO
        return false
    }

    override fun isInstantiable(type: PandaType): Boolean = true

    override fun findSubtypes(type: PandaType): Sequence<PandaType> =
        sequenceOf()

    override fun topTypeStream(): UTypeStream<PandaType> = PandaTopTypeStream(this)
}

class PandaTopTypeStream(
    private val pandaTypeSystem: PandaTypeSystem,
    private val primitiveTypes: PersistentList<PandaPrimitiveType> = persistentListOf( // TODO replace with list
        PandaNumberType,
        PandaBoolType,
        PandaStringType
    ),
    private val objectTypeStream: UTypeStream<PandaType> = USupportTypeStream.from(pandaTypeSystem, PandaObjectType),
) : UTypeStream<PandaType> {
    override fun filterBySupertype(type: PandaType): UTypeStream<PandaType> {
        if (type is PandaPrimitiveType) {
            if (type !in primitiveTypes) {
                return emptyTypeStream()
            }

            return USingleTypeStream(pandaTypeSystem, type)
        }

        return objectTypeStream.filterBySupertype(type)
    }

    override fun filterBySubtype(type: PandaType): UTypeStream<PandaType> {
        if (type is PandaPrimitiveType) {
            if (type !in primitiveTypes) {
                return emptyTypeStream()
            }

            return USingleTypeStream(pandaTypeSystem, type)
        }

        return objectTypeStream.filterBySubtype(type)
    }

    override fun filterByNotSupertype(type: PandaType): UTypeStream<PandaType> {
        if (type is PandaPrimitiveType && type in primitiveTypes) { // TODO should it be empty?????
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) {
                return objectTypeStream
            }

            return PandaTopTypeStream(pandaTypeSystem, updatedPrimitiveTypes, objectTypeStream)
        }

        return PandaTopTypeStream(pandaTypeSystem, primitiveTypes, objectTypeStream.filterByNotSupertype(type))
    }

    override fun filterByNotSubtype(type: PandaType): UTypeStream<PandaType> {
        if (type is PandaPrimitiveType && type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) {
                return objectTypeStream
            }

            return PandaTopTypeStream(pandaTypeSystem, updatedPrimitiveTypes, objectTypeStream)
        }

        return PandaTopTypeStream(pandaTypeSystem, primitiveTypes, objectTypeStream.filterByNotSubtype(type))
    }

    override fun take(n: Int): TypesResult<PandaType> {
        if (n <= primitiveTypes.size) {
            return primitiveTypes.toTypesResult(wasTimeoutExpired = false)
        }

        val types = primitiveTypes.toMutableList()
        return when (val remainingTypes = objectTypeStream.take(n - primitiveTypes.size)) {
            TypesResult.EmptyTypesResult -> types.toTypesResult(wasTimeoutExpired = false)
            is TypesResult.SuccessfulTypesResult -> {
                val allTypes = types + remainingTypes.types
                allTypes.toTypesResult(wasTimeoutExpired = false)
            }

            is TypesResult.TypesResultWithExpiredTimeout -> {
                val allTypes = types + remainingTypes.collectedTypes
                allTypes.toTypesResult(wasTimeoutExpired = true)
            }
        }
    }

    override val isEmpty: Boolean?
        get() = run {
            if (primitiveTypes.isNotEmpty()) return@run false

            return objectTypeStream.isEmpty
        }

    override val commonSuperType: PandaType?
        get() = if (primitiveTypes.isNotEmpty()) null else objectTypeStream.commonSuperType

}