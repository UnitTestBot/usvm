package org.usvm.jacodb

import org.jacodb.api.core.cfg.*

interface GoInstLocation : CoreInstLocation<GoMethod>

class GoInstLocationImpl(override val index: Int, override val lineNumber: Int, override val method: GoMethod) : GoInstLocation {
    override fun toString(): String {
        return "${method.metName}:$lineNumber"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoInstLocationImpl

        if (index != other.index) return false
        return method == other.method
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + method.hashCode()
        return result
    }
}