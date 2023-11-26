package org.usvm.util

import java.nio.file.Path
import java.util.*

class Path {
    companion object {
        private val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)

        fun getProgram(path: String): String {
            val base = when {
                os.contains("mac") || os.contains("darwin") -> "/Users/e.k.ibragimov/Documents/University/MastersDiploma/programs"
                os.contains("linux") -> "/home/buraindo/programs"
                else -> "C:/Users/burai/Documents/University/MastersDiploma/expr/programs/usvm"
            }
            return Path.of(base, path).toString()
        }

        fun getLib(path: String): String {
            val (dir, ext) = when {
                os.contains("mac") || os.contains("darwin") -> "/Users/e.k.ibragimov/Documents/University/MastersDiploma/libs" to "dylib"
                os.contains("linux") -> "/home/buraindo/libs" to "so"
                else -> "C:/Users/burai/Documents/University/MastersDiploma/expr/libs" to "dll"
            }
            return Path.of(dir, "$path.$ext").toString()
        }
    }
}