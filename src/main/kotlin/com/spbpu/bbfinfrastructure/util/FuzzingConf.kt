package com.spbpu.bbfinfrastructure.util

import com.spbpu.bbfinfrastructure.project.LANGUAGE
import java.io.File
import java.util.*

object FuzzingConf {

    private val file: File = File("psi-fuzz.conf")

    fun getPropValue(name: String): String? {
        val props = Properties()
        props.load(file.inputStream())
        return props.getProperty(name)
    }

    fun getPropValueWithoutQuotes(name: String): String {
        val props = Properties()
        props.load(file.inputStream())
        val prop = props.getProperty(name) ?: throw IllegalArgumentException("Cannot init $name property")
        val res = prop.drop(1).dropLast(1)
        return res
    }

    fun getPropAsBoolean(name: String): Boolean = getPropValue(name)?.toBoolean()
        ?: throw IllegalArgumentException("Cannot init $name property")

    fun getPropAsInt(name: String): Int = getPropValue(name)?.toIntOrNull()
        ?: throw IllegalArgumentException("Cannot init $name property")


    //PATHS
    val pathToTmpFile = getPropValueWithoutQuotes("TMPFILE")
    val pathToTmpDir = pathToTmpFile.substringBeforeLast("/")
    val pathToTmpJava = "tmpJava"
    val pathToTmpGo = "tmpGo"
    val tmpPath = "tmp/projects/"
    val pathToTemplates = getPropValueWithoutQuotes("DIR_TO_TEMPLATES")
    val pathToReportsDir = getPropValueWithoutQuotes("PATH_TO_REPORTS_DIR")
    var pathToBenchmarkToFuzz = ""
    var pathToOriginalBenchmark = ""

    val pathToOwaspJar = "lib/owaspBenchmarkClasspath.jar"
    val pathToJulietSupportJar = "lib/juliet-support.jar"
    var language = LANGUAGE.UNKNOWN


    var numberOfMutationsPerFile = getPropAsInt("MUTATIONS_PER_FILE")
    var numberOfMutantsPerFile = getPropAsInt("MUTANTS_PER_FILE")
    var badTemplatesOnlyMode = getPropAsBoolean("BAD_TEMPLATES_MODE")
    var maxNumOfObjectsTemplates = getPropAsInt("MAX_NUM_OF_OBJECTS_TEMPLATES")
    var maxNumOfSensitivityTemplates = getPropAsInt("MAX_NUM_OF_SENSITIVITY_TEMPLATES")

    val pyEnvName = getPropValueWithoutQuotes("PYENV_NAME")

    //Test mode
    var testMode = false
}