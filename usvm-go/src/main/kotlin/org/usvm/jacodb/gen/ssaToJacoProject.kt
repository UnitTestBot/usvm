package org.usvm.jacodb.gen

import org.jacodb.go.api.GoProject

interface ssaToJacoProject {
    fun createJacoDBProject(): GoProject
}
