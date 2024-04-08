package com.spbpu.bbfinfrastructure.util

import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Paths

class FuzzClassLoader : URLClassLoader(arrayOf(Paths.get(CompilerArgs.pathToOwaspJar).toUri().toURL())) {
}
