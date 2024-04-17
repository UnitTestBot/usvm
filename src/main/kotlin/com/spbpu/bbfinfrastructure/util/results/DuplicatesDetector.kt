package com.spbpu.bbfinfrastructure.util.results

import java.io.File

object DuplicatesDetector {

    fun hasDuplicates(pathToResultsDir: String, resultHeader: ResultHeader): Boolean =
        File(pathToResultsDir).listFiles()?.filter { it.isFile }
            ?.asSequence()
            ?.mapNotNull { ResultHeader.convertFromString(it.readText()) }
            ?.any { it.weakEquals(resultHeader) } ?: false
}