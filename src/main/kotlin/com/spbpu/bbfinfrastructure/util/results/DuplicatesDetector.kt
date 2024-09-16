package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.LANGUAGE
import java.io.File

object DuplicatesDetector {

    fun hasDuplicates(pathToResultsDir: String, resultHeader: ResultHeader, language: LANGUAGE): Boolean =
        File(pathToResultsDir).listFiles()?.filter { it.isFile }
            ?.asSequence()
            ?.mapNotNull { ResultHeader.convertFromString(it.readText(), language) }
            ?.any { it.weakEquals(resultHeader) } ?: false
}