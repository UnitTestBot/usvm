package org.usvm.machine.interpreter

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.ext.constructors
import org.usvm.machine.logger
import org.usvm.types.TypesResult
import org.usvm.types.UTypeStream

interface JcTypeSelector {
    fun choose(method: JcMethod, typeStream: UTypeStream<out JcType>): Collection<JcType>
}

class JcFixedInheritorsNumberTypeSelector(
    private val inheritorsNumberToChoose: Int = DEFAULT_INHERITORS_NUMBER_TO_CHOOSE,
    private val inheritorsNumberToSelectFrom: Int = DEFAULT_INHERITORS_NUMBER_TO_SCORE,
) : JcTypeSelector {

    override fun choose(method: JcMethod, typeStream: UTypeStream<out JcType>): Collection<JcType> =
        choose(method.enclosingClass, typeStream)

    fun choose(referenceClass: JcClassOrInterface, typeStream: UTypeStream<out JcType>): Collection<JcType> =
        typeStream
            .take(inheritorsNumberToSelectFrom)
            .let {
                when (it) {
                    TypesResult.EmptyTypesResult -> emptyList()
                    is TypesResult.SuccessfulTypesResult -> it
                    is TypesResult.TypesResultWithExpiredTimeout -> it.collectedTypes
                }
            }
            .sortedByDescending { type -> typeScore(referenceClass, type) }
            .take(inheritorsNumberToChoose)
            .also {
                logger.debug { "Select types for ${referenceClass.name} : ${it.map { it.typeName }}" }
            }

    private fun typeScore(referenceClass: JcClassOrInterface, type: JcType): Double {
        var score = 0.0

        if (type is JcClassType) {
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

    companion object {
        const val DEFAULT_INHERITORS_NUMBER_TO_CHOOSE: Int = 4
        // TODO: elaborate on better constant choosing
        const val DEFAULT_INHERITORS_NUMBER_TO_SCORE: Int = 100
    }
}
