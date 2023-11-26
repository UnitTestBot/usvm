package org.usvm.jacodb.gen

import org.jacodb.go.api.GoExpr
import org.jacodb.go.api.GoMethod

interface ssaToJacoExpr {
    fun createJacoDBExpr(parent: GoMethod): GoExpr
}
