package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File

class Project(
    var configuration: Header,
    var files: List<BBFFile>,
    val language: LANGUAGE = LANGUAGE.KOTLIN
) {

    constructor(configuration: Header, file: BBFFile, language: LANGUAGE) : this(configuration, listOf(file), language)

    companion object {

        fun createJavaProjectFromFiles(files: List<File>): Project {
            val javaFiles =
                files.map {
                    val text = it.readText()
                    BBFFile(it.name, PSICreator.getPsiForJava(text, Factory.file.project))
                }
            return Project(Header.createHeader(""), javaFiles, LANGUAGE.JAVA)
        }

        fun createFromCode(code: String): Project {
            val configuration = Header.createHeader(getCommentSection(code))
            val files = BBFFileFactory(code, configuration).createBBFFiles() ?: return Project(configuration, listOf())
            val language =
                when {
                    files.any { it.getLanguage() == LANGUAGE.UNKNOWN } -> LANGUAGE.UNKNOWN
                    files.any { it.getLanguage() == LANGUAGE.JAVA } -> LANGUAGE.KJAVA
                    else -> LANGUAGE.KOTLIN
                }
            return Project(configuration, files, language)
        }
    }

    fun addFile(file: BBFFile): List<BBFFile> {
        files = files + listOf(file)
        return files
    }

    fun removeFile(file: BBFFile): List<BBFFile> {
        files = files.getAllWithout(file)
        return files
    }

    fun saveToDir(dir: String): List<String> {
        File(dir).mkdirs()
        val resPaths = mutableListOf<String>()
        files.forEach {  file ->
            resPaths.add("$dir/${file.name}")
            File("$dir/${file.name}").writeText(file.psiFile.text)
        }
        return resPaths
    }

    fun saveOrRemoveToTmp(trueSaveFalseDelete: Boolean): String {
        files.forEach {
            if (trueSaveFalseDelete) {
                val name = "${CompilerArgs.pathToTmpJava}/${it.name}"
                File(name.substringBeforeLast("/")).mkdirs()
                File(name).writeText(it.psiFile.text)
            } else {
                val createdDirectories = it.name.substringAfter(CompilerArgs.pathToTmpJava).substringBeforeLast('/')
                if (createdDirectories.trim().isNotEmpty()) {
                    File("${CompilerArgs.pathToTmpJava}$createdDirectories").deleteRecursively()
                } else {
                    File(it.name).delete()
                }
            }
        }
        return files.joinToString(" ") { "${CompilerArgs.pathToTmpJava}/${it.name}" }
    }

    fun moveAllCodeInOneFile() =
        StringBuilder().apply {
            append(configuration.toString());
            if (configuration.isWithCoroutines())
                files.getAllWithoutLast().forEach { appendLine(it.toString()) }
            else files.forEach { appendLine(it.toString()) }
        }.toString()


    fun saveInOneFile(pathToSave: String) {
        val text = moveAllCodeInOneFile()
        File(pathToSave).writeText(text)
    }


    fun isBackendIgnores(backend: String): Boolean = configuration.ignoreBackends.contains(backend)

    fun getProjectSettingsAsCompilerArgs(backendType: String): CommonCompilerArguments {
        val args = when (backendType) {
            "JVM" -> K2JVMCompilerArguments()
            else -> K2JSCompilerArguments()
        }
        val languageDirective = "-XXLanguage:"
        val languageFeaturesAsArgs = configuration.languageSettings.joinToString(
            separator = " $languageDirective",
            prefix = languageDirective,
        ).split(" ")
        when (backendType) {
            "JVM" -> args.apply {
                K2JVMCompiler().parseArguments(
                    languageFeaturesAsArgs.toTypedArray(),
                    this as K2JVMCompilerArguments
                )
            }

            "JS" -> args.apply {
                K2JSCompiler().parseArguments(
                    languageFeaturesAsArgs.toTypedArray(),
                    this as K2JSCompilerArguments
                )
            }
        }
        args.optIn = configuration.useExperimental.toTypedArray()
        return args
    }

    fun addMain(): Project {
        if (files.map { it.text }.any { it.contains("fun main(") }) return Project(configuration, files, language)
        val boxFuncs =
            files.flatMap { it.psiFile.getAllPSIChildrenOfType<KtNamedFunction> { it.name?.contains("box") == true } }
        if (boxFuncs.isEmpty()) return Project(configuration, files, language)
        val indOfFile =
            files.indexOfFirst {
                it.psiFile.getAllPSIChildrenOfType<KtNamedFunction>().any { it.name?.contains("box") == true }
            }
        if (indOfFile == -1) return Project(configuration, files, language)
        val file = files[indOfFile]
        val psiCopy = file.psiFile.copy() as PsiFile
        psiCopy.addMain(boxFuncs)
        val newFirstFile = BBFFile(file.name, psiCopy)
        val newFiles =
            listOf(newFirstFile) + files.getAllWithout(indOfFile).map { BBFFile(it.name, it.psiFile.copy() as PsiFile) }
        return Project(configuration, newFiles, language)
    }

    fun copy(): Project {
        return Project(configuration, files.map { it.copy() }, language)
    }


    override fun toString(): String = files.joinToString("\n\n") {
        it.name + "\n" +
                it.psiFile.text
    }
}