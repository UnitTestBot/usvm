package org.usvm.jacodb.gen

import org.jacodb.go.api.GoType

interface ssaToJacoType {
    fun createJacoDBType(): GoType
}
