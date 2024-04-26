package org.usvm.machine.interpreter

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.constructors
import org.usvm.machine.logger
import org.usvm.types.TypesResult
import org.usvm.types.UTypeStream
import org.usvm.util.isUsvmInternalClass

interface JcTypeSelector {
    fun choose(method: JcMethod, typeStream: UTypeStream<out JcType>): Collection<JcType>
}

class JcFixedInheritorsNumberTypeSelector(
    private val inheritorsNumberToChoose: Int = DEFAULT_INHERITORS_NUMBER_TO_CHOOSE,
    inheritorsNumberToSelectFrom: Int = DEFAULT_INHERITORS_NUMBER_TO_SCORE,
) : JcTypeSelector {
    private val typesPriorities = JcTypeStreamPrioritization(inheritorsNumberToSelectFrom)

    override fun choose(method: JcMethod, typeStream: UTypeStream<out JcType>): Collection<JcType> =
        typesPriorities.take(typeStream, method.enclosingClass, inheritorsNumberToChoose)
            .also {
                logger.debug { "Select types for ${method.enclosingClass.name} : ${it.map { it.typeName }}" }
            }

    companion object {
        const val DEFAULT_INHERITORS_NUMBER_TO_CHOOSE: Int = 4
        // TODO: elaborate on better constant choosing
        const val DEFAULT_INHERITORS_NUMBER_TO_SCORE: Int = 100
    }
}

class JcTypeStreamPrioritization(private val typesToScore: Int) {
    fun take(
        typeStream: UTypeStream<out JcType>,
        referenceClass: JcClassOrInterface,
        limit: Int
    ): Collection<JcType> = fetchTypes(typeStream)
        .sortedByDescending { type -> typeScore(referenceClass, type) }
        .take(limit)

    fun firstOrNull(
        typeStream: UTypeStream<out JcType>,
        referenceClass: JcClassOrInterface,
    ): JcType? = fetchTypes(typeStream)
        .maxByOrNull { type -> typeScore(referenceClass, type) }

    private fun fetchTypes(typeStream: UTypeStream<out JcType>): Collection<JcType> =
        typeStream
            .take(typesToScore)
            .let {
                when (it) {
                    TypesResult.EmptyTypesResult -> emptyList()
                    is TypesResult.SuccessfulTypesResult -> it
                    is TypesResult.TypesResultWithExpiredTimeout -> it.collectedTypes
                }
            }

    private fun typeScore(referenceClass: JcClassOrInterface, type: JcType): Double {
        var score = 0.0

        if (type is JcClassType) {
            /**
             * TODO: USVM internal classes should not appear in type streams,
             *  but for now they are required for isSubtypes checks and virtual calls.
             *  So we give them the lowest possible priority to avoid such classes when they
             *  don't really needed (e.g. forced by type constraints).
             **/
            if (type.isUsvmInternalClass) {
                return Double.NEGATIVE_INFINITY
            }

            // prefer class types over arrays
            score += 1

            if (type.isPublic) {
                score += 3
            }

            // Prefer easy instantiable classes
            if (type.constructors.any { it.isPublic }) {
                score += 3
            }

            if (type.isFinal) {
                score += 1
            }

            if (type.outerType == null) {
                score += 1
            }

            val typePkg = type.jcClass.name.split(".")
            val methodPkg = referenceClass.name.split(".")

            for ((typePkgPart, methodPkgPart) in typePkg.zip(methodPkg)) {
                if (typePkgPart != methodPkgPart) break
                score += 1
            }
        }

        if (type is JcArrayType) {
            val elementScore = typeScore(referenceClass, type.elementType)
            score += elementScore / 10
        }

        return score
    }
}
