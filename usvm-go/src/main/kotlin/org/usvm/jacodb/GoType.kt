package org.usvm.jacodb

import org.jacodb.api.core.CoreType

interface GoType : CoreType

class NullType: GoType {
    override val typeName = "null"
}

class LongType: GoType {
    override val typeName = "long"
}
