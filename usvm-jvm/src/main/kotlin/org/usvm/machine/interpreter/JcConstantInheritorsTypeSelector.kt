package org.usvm.machine.interpreter

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.types.UTypeStream

interface JcTypeSelector {
    fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType>
}

class JcConstantInheritorsTypeSelector(
    private val inheritorsToChoose: Int = DEFAULT_INHERITORS_TO_CHOOSE,
) : JcTypeSelector {
    override fun choose(method: JcMethod, typeStream: UTypeStream<JcType>): Collection<JcType> {
        return typeStream.take(inheritorsToChoose)
    }

    companion object {
        const val DEFAULT_INHERITORS_TO_CHOOSE = 4
    }
}