package org.usvm.machine.resolver

import org.jacodb.api.JcType
import org.usvm.types.UTypeStream

class JcTypeSelector {
    fun choose(typeStream: UTypeStream<JcType>): Collection<JcType> {
        return typeStream.take(5)
    }
}