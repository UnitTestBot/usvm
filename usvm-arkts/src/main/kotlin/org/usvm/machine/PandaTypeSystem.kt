package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.panda.dynamic.api.PandaArrayType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaMethod
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
        if (type is PandaPrimitiveType) return types.isEmpty()

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

interface PandaTypeSelector {
    fun choose(method: PandaMethod, typeStream: UTypeStream<out PandaType>): Collection<PandaType>
}

class PandaFixedInheritorsNumberTypeSelector(
    private val inheritorsNumberToChoose: Int = DEFAULT_INHERITORS_NUMBER_TO_CHOOSE,
    inheritorsNumberToSelectFrom: Int = DEFAULT_INHERITORS_NUMBER_TO_SCORE,
) : PandaTypeSelector {
    private val typesPriorities = PandaTypeStreamPrioritization(inheritorsNumberToSelectFrom)

    override fun choose(method: PandaMethod, typeStream: UTypeStream<out PandaType>): Collection<PandaType> =
        typesPriorities.take(typeStream, method.enclosingClass, inheritorsNumberToChoose)

    companion object {
        const val DEFAULT_INHERITORS_NUMBER_TO_CHOOSE: Int = 4
        // TODO: elaborate on better constant choosing
        const val DEFAULT_INHERITORS_NUMBER_TO_SCORE: Int = 100
    }
}

class PandaTypeStreamPrioritization(private val typesToScore: Int) {
    fun take(
        typeStream: UTypeStream<out PandaType>,
        referenceClass: PandaClass,
        limit: Int
    ): Collection<PandaType> = fetchTypes(typeStream)
        .sortedByDescending { type -> typeScore(referenceClass, type) }
        .take(limit)

    fun firstOrNull(
        typeStream: UTypeStream<out PandaType>,
        referenceClass: PandaClass,
    ): PandaType? = fetchTypes(typeStream)
        .maxByOrNull { type -> typeScore(referenceClass, type) }

    private fun fetchTypes(typeStream: UTypeStream<out PandaType>): Collection<PandaType> =
        typeStream
            .take(typesToScore)
            .let {
                when (it) {
                    TypesResult.EmptyTypesResult -> emptyList()
                    is TypesResult.SuccessfulTypesResult -> it
                    is TypesResult.TypesResultWithExpiredTimeout -> it.collectedTypes
                }
            }

    private fun typeScore(referenceClass: PandaClass, type: PandaType): Double {
        var score = 0.0

        if (type is PandaClassType) {
            // prefer class types over arrays
            score += 1

//            if (type.isPublic) {
//                score += 3
//            }
//
//            // Prefer easy instantiable classes
//            if (type.constructors.any { it.isPublic }) {
//                score += 3
//            }
//
//            if (type.isFinal) {
//                score += 1
//            }
//
//            if (type.outerType == null) {
//                score += 1
//            }
//
//            val typePkg = type.jcClass.name.split(".")
//            val methodPkg = referenceClass.name.split(".")
//
//            for ((typePkgPart, methodPkgPart) in typePkg.zip(methodPkg)) {
//                if (typePkgPart != methodPkgPart) break
//                score += 1
//            }
        }

        if (type is PandaArrayType) {
            val elementScore = typeScore(referenceClass, type.elementType)
            score += elementScore / 10
        }

        return score
    }
}
