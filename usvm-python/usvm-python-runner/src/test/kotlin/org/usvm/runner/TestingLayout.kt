package org.usvm.runner

import java.io.File

class TestingLayout(basePath: String) : DistributionLayout {
    override val cpythonPath = File(basePath, "cpythonadapter/build/cpython_build")
    override val approximationsPath = File(basePath, "python_approximations")
    override val nativeLibPath = File(basePath, "cpythonadapter/build/lib/main/debug")
    override val jarPath = File(basePath, "build/libs/usvm-python.jar")
}
