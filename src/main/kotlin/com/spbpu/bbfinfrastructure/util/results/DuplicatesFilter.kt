package com.spbpu.bbfinfrastructure.util.results

import java.io.File

class DuplicatesFilter(private val pathToResultsDir: String) {

    fun hasDuplicates(resultHeader: ResultHeader): Boolean =
        File(pathToResultsDir).listFiles()!!.filter { it.isFile }
            .asSequence()
            .mapNotNull { ResultHeader.convertFromString(it.readText()) }
            .any { it.weakEquals(resultHeader) }
}