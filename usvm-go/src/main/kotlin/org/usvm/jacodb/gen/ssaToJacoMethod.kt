package org.usvm.jacodb.gen

import org.jacodb.go.api.*

interface ssaToJacoMethod {
    fun createJacoDBMethod(fileSet: FileSet): GoMethod
}
