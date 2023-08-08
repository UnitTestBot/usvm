package org.usvm.machine.interpreter

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.types.UTypeStream

interface JcTypeSelector {
    fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType>
}

class JcFixedInheritorsNumberTypeSelector(
    private val inheritorsNumberToChoose: Int = DEFAULT_INHERITORS_NUMBER_TO_CHOOSE,
) : JcTypeSelector {
    override fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType> {
        return typeStream.take(inheritorsNumberToChoose)
    }

    companion object {
        const val DEFAULT_INHERITORS_NUMBER_TO_CHOOSE: Int = 4
    }
}