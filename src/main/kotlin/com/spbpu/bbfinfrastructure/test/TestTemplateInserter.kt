package com.spbpu.bbfinfrastructure.test

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.RandomTypeGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesInserter
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import kotlin.random.Random
import kotlin.system.exitProcess

class TestTemplatesInserter : Transformation() {

    private val originalPsiText = file.text

    fun testTransform(templateText: String, templateBodyIndex: Int): Boolean {
        val parsedTemplates = parseTemplate(templateText)!!
        for ((ind, randomTemplateBody) in parsedTemplates.templates.withIndex()) {
            if (ind != templateBodyIndex) continue
            tryToAddTemplate(parsedTemplates, randomTemplateBody).ifFalse {
                return false
            }

        }
        return true
    }

    private fun tryToAddTemplate(
        parsedTemplates: TemplatesInserter.Template,
        randomTemplateBody: TemplatesInserter.TemplateBody
    ): Boolean {
        repeat(1_000) {
            val fileBackupText = file.text
            try {
                tryToTransform(parsedTemplates, randomTemplateBody).ifTrue {
                    checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
                    return true
                }
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            }
        }
        return false
    }

    private fun tryToTransform(
        parsedTemplates: TemplatesInserter.Template,
        randomTemplate: TemplatesInserter.TemplateBody
    ): Boolean {
        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
        insertClasses(parsedTemplates)
        insertImports(parsedTemplates)
        insertAuxMethods(randomPlaceToInsert, randomTemplate).let { if (!it) return false }
        val newText = generateNewBody(randomPlaceToInsert, randomTemplate)
        val newPsiBlock =
            try {
                Factory.javaPsiFactory.createCodeBlockFromText("{\n$newText\n}", null).also {
                    it.lBrace!!.delete()
                    it.rBrace!!.delete()
                }
            } catch (e: Throwable) {
                return false
            }
        randomPlaceToInsert.replaceThis(newPsiBlock)
        return checkNewCode()
    }


    protected fun checkNewCode(): Boolean {
        return if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            true
        }
    }

    protected open fun generateNewBody(
        randomPlaceToInsert: PsiElement,
        randomTemplate: TemplatesInserter.TemplateBody
    ): String {
        val randomTemplateBody = randomTemplate.templateBody
        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
        val mappedHoles = mutableMapOf<String, String>()
        val mappedTypes = mutableMapOf<String, String>()
        val regex = Regex("""~\[(.*?)\]~""")
        val expressionGenerator = ExpressionGenerator()
        val newText = regex.replace(randomTemplateBody) replace@{ result ->
            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
            if (hole.contains("@")) {
                mappedHoles[hole]?.let { return@replace it }
            }
            val holeType =
                when {
                    hole.startsWith("TYPE") -> HOLE_TYPE.TYPE
                    hole.startsWith("VAR_") -> HOLE_TYPE.VAR
                    hole.startsWith("CONST_") -> HOLE_TYPE.CONST
                    else -> HOLE_TYPE.EXPR
                }
            if (holeType == HOLE_TYPE.CONST) {
                val literal = expressionGenerator.genConstant(getTypeFromHole(hole, mappedTypes)!!)!!
                mappedHoles[hole] = literal
                return@replace literal
            }
            if (holeType == HOLE_TYPE.TYPE) {
                mappedTypes[hole]?.let { return@replace it }
                val randomType = RandomTypeGenerator.generateRandomType()
                mappedTypes[hole] = randomType
                return@replace randomType
            }
            val type = getTypeFromHole(hole, mappedTypes) ?: run {
                RandomTypeGenerator.generateRandomType().also {
                    mappedTypes[hole.substringAfter("_")] = it
                }
            }
            val capturedType = JavaTypeMappings.mappings[type] ?: type
            if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
                if (holeType == HOLE_TYPE.EXPR) {
                    ConditionGenerator(scope).generate()?.let {
                        if (hole.contains("@")) {
                            mappedHoles[hole] = it
                        }
                        return@replace it
                    }
                }
            }
            val randomValueWithCompatibleType =
                if (Random.getTrue(20) || holeType == HOLE_TYPE.VAR) {
                    if (capturedType == "java.lang.Object") {
                        scope.randomOrNull()?.name
                    } else {
                        getValueOfTypeFromScope(scope, capturedType)
                    }
                } else null
            if (holeType == HOLE_TYPE.VAR && randomValueWithCompatibleType == null) {
                ErrorCollector.putError("Can't find variable with type $capturedType in the scope")
                throw IllegalArgumentException()
            }
            val resMapping =
                randomValueWithCompatibleType
                    ?: expressionGenerator.generateExpressionOfType(scope, capturedType)
                    ?: throw IllegalArgumentException()
            if (hole.contains("@")) {
                mappedHoles[hole] = resMapping
            }
            resMapping
        }
        return newText
    }

    protected fun insertClasses(parsedTemplates: TemplatesInserter.Template) {
        for (auxClass in parsedTemplates.auxClasses) {
            val bbfFile =
                BBFFile("${auxClass.first.substringAfterLast('.')}.java", PSICreator.getPsiForJava(auxClass.second))
            project.addFile(bbfFile)
        }
    }

    protected fun insertImports(parsedTemplates: TemplatesInserter.Template) {
        if (parsedTemplates.imports.isNotEmpty()) {
            val oldImportList = (file as PsiJavaFile).importList?.text ?: ""
            val additionalImports = parsedTemplates.imports.joinToString("\n") { "import $it" }
            val newImportList =
                (PSICreator.getPsiForJava("$oldImportList\n$additionalImports") as PsiJavaFile).importList!!
            (file as PsiJavaFile).importList?.replaceThis(newImportList) ?: return
        }
    }

    protected fun insertAuxMethods(
        randomPlaceToInsert: PsiElement,
        randomTemplate: TemplatesInserter.TemplateBody
    ): Boolean {
        val auxMethods = randomTemplate.auxMethods
        for (auxMethod in auxMethods) {
            val psiClass = randomPlaceToInsert.parents.find { it is PsiClass } as? PsiClassImpl ?: return false
            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod, null)
            val lastMethod =
                psiClass.getAllChildrenOfCurLevel().findLast { it is PsiMethod && it.containingClass == psiClass }
                    ?: return false
            lastMethod.addAfterThisWithWhitespace(m, "\n\n")
        }
        return true
    }

    private fun getTypeFromHole(hole: String, mappedTypes: Map<String, String>) =
        if (hole.contains("TYPE")) {
            mappedTypes[hole.substringAfter("_")]
        } else {
            hole.substringAfter("_").substringBefore("@")
        }

    private fun getValueOfTypeFromScope(scope: List<JavaScopeCalculator.JavaScopeComponent>, type: String): String? {
        val jType =
            try {
                this::class.java.classLoader.loadClass(type)
            } catch (e: Throwable) {
                null
            }
        return jType?.let { jTypeNotNull ->
            scope.filter { it.type == jTypeNotNull.name || it.type == jTypeNotNull.primitiveByWrapper?.name }
                .randomOrNull()?.name
        } ?: scope.filter { it.type == type }.randomOrNull()?.name
    }

    companion object {
        fun parseTemplate(template: String): TemplatesInserter.Template? {
            val regexForAuxClasses =
                Regex("""~class\s+(\S+)\s+start~\s*(.*?)\s*~class\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
            val foundAuxClasses = regexForAuxClasses.findAll(template)
            val auxClasses = mutableListOf<Pair<String, String>>()
            val imports = mutableListOf<String>()
            for (auxClass in foundAuxClasses) {
                val className = auxClass.groupValues[1]
                val classBody = auxClass.groupValues[2].trim()
                auxClasses.add(className to classBody)
            }
            val regexForMainClass =
                Regex("""~main class start~\s*(.*?)\s*~main class end~""", RegexOption.DOT_MATCHES_ALL)
            val mainClassTemplateBody = regexForMainClass.find(template)?.groupValues?.lastOrNull() ?: return null
            val importsRegex = Regex("""~import (.*?)~""", RegexOption.DOT_MATCHES_ALL)
            val templateRegex = Regex("""~template start~\s*(.*?)\s*~template end~""", RegexOption.DOT_MATCHES_ALL)
            val auxMethodsRegex =
                Regex("""~function\s+(\S+)\s+start~\s*(.*?)\s*~function\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
            importsRegex.findAll(mainClassTemplateBody).forEach { imports.add(it.groupValues.last()) }
            val templatesBodies =
                templateRegex.findAll(mainClassTemplateBody)
                    .map {
                        val body = it.groupValues.last()
                        val auxMethods = auxMethodsRegex.findAll(body).map { it.groupValues.last() }.toList()
                        val templateBody = body.substringAfterLast("end~\n")
                        TemplatesInserter.TemplateBody(auxMethods, templateBody)
                    }
                    .toList()
            return TemplatesInserter.Template(auxClasses, imports, templatesBodies)
        }
    }

    private enum class HOLE_TYPE {
        VAR, EXPR, TYPE, CONST
    }

    override fun transform() {
        TODO("Not yet implemented")
    }
}