package org.usvm.jacodb.gen

import org.jacodb.go.api.GoValue
import org.jacodb.go.api.GoMethod

interface ssaToJacoValue {
    fun createJacoDBValue(parent: GoMethod): GoValue
}
