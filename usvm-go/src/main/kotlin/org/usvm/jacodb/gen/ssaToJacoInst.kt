package org.usvm.jacodb.gen

import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod

interface ssaToJacoInst {
    fun createJacoDBInst(parent: GoMethod): GoInst
}
