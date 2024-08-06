package com.spbpu.bbfinfrastructure.util

import java.net.URLClassLoader
import java.nio.file.Paths

class FuzzClassLoader : URLClassLoader(arrayOf(Paths.get(FuzzingConf.pathToOwaspJar).toUri().toURL())) {
}
