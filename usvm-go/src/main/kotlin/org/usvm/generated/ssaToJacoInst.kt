package org.usvm.generated

import org.usvm.jacodb.GoInst
import org.usvm.jacodb.GoMethod

interface ssaToJacoInst {
    fun createJacoDBInst(parent: GoMethod): GoInst
}
