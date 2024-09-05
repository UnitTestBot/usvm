package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.intellij.psi.PsiElement
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ScopeComponent
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.getLocationLineNumber
import com.spbpu.bbfinfrastructure.util.getRandomPlaceToInsertNewLine
import com.spbpu.bbfinfrastructure.util.randomOrNullWithIndex
import com.spbpu.bbfinfrastructure.util.statistic.StatsManager
import kotlin.random.Random

abstract class BaseTemplatesInserter: Transformation() {

    protected var addedProjects = 0
    protected val numberOfProjectsToCheck = FuzzingConf.numberOfMutantsPerFile
    protected val currentMutationChain = mutableListOf<MutationInfo>()
    protected val numToInsertObjectTemplates =
        if (FuzzingConf.maxNumOfObjectsTemplates == 0) {
            0
        } else {
            Random.nextInt(1, FuzzingConf.maxNumOfObjectsTemplates + 1)
        }
    protected val numToInsertSensitivityTemplates =
        if (FuzzingConf.maxNumOfSensitivityTemplates == 0) {
            0
        } else {
            Random.nextInt(1, FuzzingConf.maxNumOfSensitivityTemplates + 1)
        }
    protected var insertingObjectsTemplates = numToInsertObjectTemplates != 0
    protected var addedObjectsTemplates = 0
    protected var addedSensitivityTemplates = 0


    protected abstract fun tryToTransform(): Boolean
    protected abstract fun getReplacementForHole(
        hole: String,
        mappedTypes: MutableMap<String, String>,
        mappedHoles: MutableMap<String, String>,
        scope: List<ScopeComponent>,
        randomTemplateBody: String,
        parsedTemplate: TemplatesParser.Template,
        iteration: Int,
        usedExtensions: MutableList<String>
    ): String


    protected fun fillBlocks(
        randomTemplate: TemplatesParser.TemplateBody,
        randomPlaceToInsert: PsiElement,
        scope: List<ScopeComponent>,
        parsedTemplate: TemplatesParser.Template,
        usedExtensions: MutableList<String>,
        isPython: Boolean = false
    ): List<Pair<String, Int>>? {
        val mappedHoles = mutableMapOf<String, String>()
        val mappedTypes = mutableMapOf<String, String>()
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        var currentBlockLineNumber = randomPlaceToInsertLineNumber
        return randomTemplate.templateBody.split("~[BODY]~")
            .map { block ->
                val endOfBlock = file.getRandomPlaceToInsertNewLine(currentBlockLineNumber, !insertingObjectsTemplates && !isPython)
                    ?: return null
                var fillIteration = 0
                var filledTemplate =
                    fillTemplateBody(
                        mappedTypes = mappedTypes,
                        mappedHoles = mappedHoles,
                        scope = scope,
                        randomTemplateBody = block,
                        parsedTemplate = parsedTemplate,
                        iteration = fillIteration++,
                        usedExtensions = usedExtensions
                    )
                while (filledTemplate.contains("~[")) {
                    filledTemplate = fillTemplateBody(
                        mappedTypes = mappedTypes,
                        mappedHoles = mappedHoles,
                        scope = scope,
                        randomTemplateBody = filledTemplate,
                        parsedTemplate = parsedTemplate,
                        iteration = fillIteration++,
                        usedExtensions = usedExtensions
                    )
                }
                filledTemplate to currentBlockLineNumber.also {
                    currentBlockLineNumber = endOfBlock.getLocationLineNumber()
                }
            }
    }

    protected fun fillTemplateBody(
        mappedTypes: MutableMap<String, String>,
        mappedHoles: MutableMap<String, String>,
        scope: List<ScopeComponent>,
        randomTemplateBody: String,
        parsedTemplate: TemplatesParser.Template,
        iteration: Int,
        usedExtensions: MutableList<String>
    ): String {
        val stack = mutableListOf<Int>()
        var newText = randomTemplateBody
        var i = 0
        while (i < newText.length) {
            if (newText.startsWith("~[", i)) {
                stack.add(i)
            }
            if (newText.startsWith("]~", i)) {
                val startIndex = stack.removeLast()
                val token = newText.substring(startIndex + 2, i)
                val replacement = getReplacementForHole(
                    hole = token,
                    mappedTypes = mappedTypes,
                    mappedHoles = mappedHoles,
                    scope = scope,
                    randomTemplateBody = randomTemplateBody,
                    parsedTemplate = parsedTemplate,
                    iteration = iteration,
                    usedExtensions = usedExtensions
                )
                newText = newText.replaceRange(startIndex, i + 2, replacement)
                val diff = token.length - replacement.length
                i = i - diff - 3
            }
            i++
        }

        return newText
    }

    protected fun getRandomObjectTemplate(): Triple<TemplatesParser.Template, TemplatesParser.TemplateBody, Pair<String, Int>>? {
        val randomTemplateFile = TemplatesDB.getRandomObjectTemplate() ?: error("Cant find any template")
        val parsedTemplate = TemplatesParser.parse(randomTemplateFile.path)
        val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
        return Triple(parsedTemplate, randomTemplate, randomTemplateFile.path to randomTemplateIndex)
    }

    protected fun getRandomSensitivityTemplate(): Triple<TemplatesParser.Template, TemplatesParser.TemplateBody, Pair<String, Int>>? {
        return if (FuzzingConf.badTemplatesOnlyMode) {
            StatsManager.currentBadTemplatesList.randomOrNull()?.let {
                val index = it.second.substringAfter(' ').toInt()
                val body = it.first.templates.get(index)
                Triple(it.first, body, it.second.substringBefore(' ') to index)
            }
        } else {
            val randomTemplateFile = TemplatesDB.getRandomSensitivityTemplate() ?: error("Cant find any template")
            val parsedTemplate = TemplatesParser.parse(randomTemplateFile.path)
            val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
            Triple(parsedTemplate, randomTemplate, randomTemplateFile.path to randomTemplateIndex)
        }
    }


    protected fun checkFromExtensionsAndMacros(template: TemplatesParser.Template, hole: String): String? {
        val extensions = template.extensions[hole] ?: listOf()
        val macros = template.macros[hole] ?: listOf()
        return (extensions + macros).randomOrNull()
    }

    protected fun getTypeFromHole(hole: String, mappedTypes: Map<String, String>) =
        if (hole.contains("TYPE")) {
            mappedTypes[hole.substringAfter("_")]
        } else {
            hole.substringAfter("_").substringBefore("@")
        }

    protected enum class HOLE_TYPE {
        VAR, EXPR, TYPE, CONST, MACRO
    }
}