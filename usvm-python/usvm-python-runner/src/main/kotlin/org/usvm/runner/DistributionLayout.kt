package org.usvm.runner

import java.io.File

interface DistributionLayout {
    val cpythonPath: File
    val approximationsPath: File
    val nativeLibPath: File
    val jarPath: File
}

class StandardLayout(distributionPath: File) : DistributionLayout {
    override val cpythonPath: File = File(distributionPath, "cpython")
    override val approximationsPath: File = File(distributionPath, "lib")
    override val nativeLibPath: File = File(distributionPath, "lib")
    override val jarPath: File = File(distributionPath, "jar/usvm-python.jar")
}
