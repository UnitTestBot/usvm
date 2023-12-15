package org.usvm.util

import java.util.*

class Path {
    companion object {
        private val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)

        fun getProgram(path: String): String {
            val base = when {
                os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0 -> "/Users/e.k.ibragimov/Documents/University/MastersDiploma/programs"
                else -> "/home/buraindo/programs"
            }
            return "$base/$path"
        }

        fun getLib(path: String): String {
            val base = when {
                os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0 -> "/Users/e.k.ibragimov/Documents/University/MastersDiploma/libs"
                else -> "/home/buraindo/libs"
            }
            return "$base/$path"
        }
    }
}