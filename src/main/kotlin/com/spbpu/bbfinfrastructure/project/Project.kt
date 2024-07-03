package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File

class Project(
    var configuration: Metadata,
    var files: List<BBFFile>,
    val language: LANGUAGE = LANGUAGE.KOTLIN
) {

    constructor(configuration: Metadata, file: BBFFile, language: LANGUAGE) : this(
        configuration,
        listOf(file),
        language
    )

    companion object {

        fun createJavaProjectFromFiles(
            files: List<File>,
            originalFileName: String = "",
            originalCWEs: List<Int> = listOf(),
            region: ToolsResultsSarifBuilder.ResultRegion? = null,
            uri: String? = null,
            originalUri: String? = null
        ): Project {
            val javaFiles =
                files.map {
                    val text = it.readText()
                    BBFFile(it.name, PSICreator.getPsiForJava(text))
                }
            return Project(Metadata(originalFileName, originalCWEs, originalUri, uri, region), javaFiles, LANGUAGE.JAVA)
        }

        fun createJavaProjectFromCode(code: String, name: String): Project {
            val bbfFile = BBFFile(name, PSICreator.getPsiForJava(code, Factory.file.project))
            return Project(Metadata.createEmptyHeader(), listOf(bbfFile), LANGUAGE.JAVA)
        }

        fun createFromCode(code: String): Project {
            TODO()
//            val configuration = Header.createEmptyHeader()
//            val files = BBFFileFactory(code, configuration).createBBFFiles() ?: return Project(configuration, listOf())
//            val language =
//                when {
//                    files.any { it.getLanguage() == LANGUAGE.UNKNOWN } -> LANGUAGE.UNKNOWN
//                    files.any { it.getLanguage() == LANGUAGE.JAVA } -> LANGUAGE.KJAVA
//                    else -> LANGUAGE.KOTLIN
//                }
//            return Project(configuration, files, language)
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
        files.forEach { file ->
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
        return Project(configuration.copy(), files.map { it.copy() }, language)
    }


    override fun toString(): String = files.joinToString("\n\n") {
        it.name + "\n" +
                it.psiFile.text
    }
}