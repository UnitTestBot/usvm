package org.usvm.machine.interpreter

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.machine.logger
import org.usvm.types.UTypeStream

interface JcTypeSelector {
    fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType>
}

class JcFixedInheritorsNumberTypeSelector(
    private val inheritorsNumberToChoose: Int = DEFAULT_INHERITORS_NUMBER_TO_CHOOSE,
) : JcTypeSelector {

    override fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType> {
        return typeStream
            .take(DEFAULT_INHERITORS_NUMBER_TO_SCORE)
            .sortedByDescending { type -> typeScore(method, type) }
            .take(inheritorsNumberToChoose)
            .also {
                logger.info { "Select types for ${method.enclosingClass.name} : ${it.map { it.typeName }}" }
            }
    }

    private fun typeScore(method: JcMethod, type: JcType): Double {
        var score = 0.0

        if (type is JcClassType) {
            // prefer class types over arrays
            score += 1

            if (type.isPublic) {
                score += 1
            }

            if (type.isFinal) {
                score += 1
            }

            if (type.outerType == null) {
                score += 1
            }

            val typePkg = type.jcClass.name.split(".")
            val methodPkg = method.enclosingClass.name.split(".")

            for ((typePkgPart, methodPkgPart) in typePkg.zip(methodPkg)) {
                if (typePkgPart != methodPkgPart) break
                score += 1
            }
        }

        if (type is JcArrayType) {
            val elementScore = typeScore(method, type.elementType)
            score += elementScore / 10
        }

        return score
    }

    companion object {
        const val DEFAULT_INHERITORS_NUMBER_TO_CHOOSE: Int = 4
        const val DEFAULT_INHERITORS_NUMBER_TO_SCORE: Int = 100
    }
}