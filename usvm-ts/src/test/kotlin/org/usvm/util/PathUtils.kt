package org.usvm.util

import java.nio.file.Path
import kotlin.io.path.div

fun Path.resolveHome(): Path =
    if (startsWith("~" + fileSystem.separator)) {
        fileSystem.getPath(System.getProperty("user.home")) / subpath(1, nameCount)
    } else {
        this
    }
