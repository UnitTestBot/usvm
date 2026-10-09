package org.usvm

import java.io.File

internal fun generatedGoFile(name: String): File = File(System.getProperty("usvm.go.generatedDir"), name)
