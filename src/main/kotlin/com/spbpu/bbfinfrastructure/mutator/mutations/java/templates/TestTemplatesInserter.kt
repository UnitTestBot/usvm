//package com.spbpu.bbfinfrastructure.mutator.mutations.java
//
//import com.intellij.psi.PsiClass
//import com.intellij.psi.PsiFile
//import com.intellij.psi.PsiJavaFile
//import com.intellij.psi.PsiMethod
//import com.intellij.psi.impl.source.PsiClassImpl
//import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
//import com.spbpu.bbfinfrastructure.project.BBFFile
//import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
//import com.spbpu.bbfinfrastructure.project.JavaTestSuite
//import com.spbpu.bbfinfrastructure.psicreator.PSICreator
//import com.spbpu.bbfinfrastructure.psicreator.util.Factory
//import com.spbpu.bbfinfrastructure.util.*
//import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
//import org.jetbrains.kotlin.psi.psiUtil.parents
//import kotlin.random.Random
//import kotlin.system.exitProcess
//
//class TemplatesInserter : Transformation() {
//
//    private val testSuite = GlobalTestSuite.javaTestSuite
//    private val originalPsiText = file.text
//    private val numOfSuccessfulMutationsToAdd = 1
//    private var curNumOfSuccessfulMutations = 0
//    private var addedProjects = 0
//    private val numberOfProjectsToCheck = 10
//    private val feature = "CYCLES"
//
//    override fun transform() {
//        repeat(1_000_000) {
//            val fileBackupText = file.text
//            println("TRY $it")
//            try {
//                maxInd1 = TemplatesDB.getTemplatesForFeature(feature)!![ind1].length
//                maxInd2 = parseTemplate(TemplatesDB.getTemplatesForFeature(feature)!![ind1])!!.templates.size
//                tryToTransform1()
//            } catch (e: IllegalStateException) {
//                if (ind2 == maxInd2 - 1) {
//                    ind1++
//                    ind2 = 0
//                } else {
//                    ind2++
//                    println("TEMPLATE = ${parseTemplate(TemplatesDB.getTemplatesForFeature(feature)!![ind1])!!.templates[ind2]}")
//                }
//                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
//            }
//            catch (e: MutationFinishedException) {
//                return
//            } catch (e: Throwable) {
//                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
//            }
//        }
//
//    }
//
//    var ind1 = 0
//    var ind2 = 0
//    var maxInd1 = 0
//    var maxInd2 = 0
//
//    private fun tryToTransform1(): Boolean {
//        println("IND1 = $ind1 IND2 = $ind2")
//        val randomTemplate = (TemplatesDB.getTemplatesForFeature(feature))!![ind1] ?: return false
////        val randomTemplate = (TemplatesDB.getTemplatesForFeature("PATH_SENSITIVITY"))?.randomOrNull() ?: return false
//        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
//        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
//        val regex = Regex("""~\[(.*?)\]~""")
//        val parsedTemplates = parseTemplate(randomTemplate) ?: return false
//        for (auxClass in parsedTemplates.auxClasses) {
//            val bbfFile =
//                BBFFile("${auxClass.first.substringAfterLast('.')}.java", PSICreator.getPsiForJava(auxClass.second))
//            project.addFile(bbfFile)
//        }
//        if (parsedTemplates.imports.isNotEmpty()) {
//            val oldImportList = (file as PsiJavaFile).importList?.text ?: ""
//            val additionalImports = parsedTemplates.imports.joinToString("\n") { "import $it" }
//            val newImportList =
//                (PSICreator.getPsiForJava("$oldImportList\n$additionalImports") as PsiJavaFile).importList!!
//            (file as PsiJavaFile).importList?.replaceThis(newImportList) ?: return false
//        }
//        println("TEMPLATES = ${parsedTemplates.templates.size}")
//        val (auxMethods, randomTemplateBody) = parsedTemplates.templates.randomOrNull() ?: return false
//        for (auxMethod in auxMethods) {
//            val psiClass = randomPlaceToInsert.parents.find { it is PsiClass } as? PsiClassImpl ?: return false
//            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod, null)
//            val lastMethod = psiClass.getAllChildrenOfCurLevel().findLast { it is PsiMethod && it.containingClass == psiClass } ?: return false
//            lastMethod.addAfterThisWithWhitespace(m, "\n\n")
//        }
//        if (ind1 == 3 && ind2 == 6) {
//            println()
//        }
//
//        val newText = regex.replace(randomTemplateBody) { result ->
//            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
//            if (hole.startsWith("CONST_")) {
//                val type = hole.substringAfter("CONST_")
//                return@replace ExpressionGenerator().genConstant(type) ?: throw IllegalArgumentException()
//            }
//            val isVar = hole.startsWith("VAR_")
//            val type = if (isVar) hole.substringAfter("VAR_") else hole
//            val capturedType = JavaTypeMappings.mappings[type] ?: type
//            if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
//                ConditionGenerator(scope).generate()?.let { return@replace it }
//            }
//            val isAssign = try {
//                randomTemplateBody.substring(result.groups[0]!!.range.last + 1).let {
//                    it.startsWith(" =") || it.startsWith(" +=") || it.startsWith(" -=")
//                }
//            } catch (e: Throwable) {
//                false
//            }
//            val randomValueWithCompatibleType =
//                if (capturedType == "java.lang.Object") {
//                    scope.randomOrNull()?.name
//                } else {
//                    if (Random.getTrue(20) || isAssign || isVar) {
//                        scope.filter { it.type == capturedType }
//                            .randomOrNull()?.name
//                    } else {
//                        null
//                    }
//                }
//            if ((isVar || isAssign) && randomValueWithCompatibleType == null) {
//                println("CANT FIND VARIABLE OF TYPE $capturedType for assignment")
//                throw IllegalArgumentException()
//            }
//            randomValueWithCompatibleType
//                ?: ExpressionGenerator().generateExpressionOfType(scope, capturedType)
//                ?: throw IllegalArgumentException()
//        }
//        val newPsiBlock =
//            try {
//                Factory.javaPsiFactory.createCodeBlockFromText("{\n$newText\n}", null).also {
//                    it.lBrace!!.delete()
//                    it.rBrace!!.delete()
//                }
//            } catch (e: Throwable) {
//                return false
//            }
//        randomPlaceToInsert.replaceThis(newPsiBlock)
//        if (!checker.checkCompiling()) {
//            throw IllegalArgumentException()
//        } else {
//            throw IllegalStateException()
//        }
//    }
//
//
//    private fun parseTemplate(template: String): Template? {
//        val regexForAuxClasses =
//            Regex("""~class\s+(\S+)\s+start~\s*(.*?)\s*~class\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
//        val foundAuxClasses = regexForAuxClasses.findAll(template)
//        val auxClasses = mutableListOf<Pair<String, String>>()
//        val imports = mutableListOf<String>()
//        for (auxClass in foundAuxClasses) {
//            val className = auxClass.groupValues[1]
//            val classBody = auxClass.groupValues[2].trim()
//            auxClasses.add(className to classBody)
//        }
//        val regexForMainClass = Regex("""~main class start~\s*(.*?)\s*~main class end~""", RegexOption.DOT_MATCHES_ALL)
//        val mainClassTemplateBody = regexForMainClass.find(template)?.groupValues?.lastOrNull() ?: return null
//        val importsRegex = Regex("""~import (.*?)~""", RegexOption.DOT_MATCHES_ALL)
//        val templateRegex = Regex("""~template start~\s*(.*?)\s*~template end~""", RegexOption.DOT_MATCHES_ALL)
//        val auxMethodsRegex =
//            Regex("""~function\s+(\S+)\s+start~\s*(.*?)\s*~function\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
//        importsRegex.findAll(mainClassTemplateBody).forEach { imports.add(it.groupValues.last()) }
//        val templatesBodies =
//            templateRegex.findAll(mainClassTemplateBody)
//                .map {
//                    val body = it.groupValues.last()
//                    val auxMethods = auxMethodsRegex.findAll(body).map { it.groupValues.last() }.toList()
//                    val templateBody = body.substringAfterLast("end~\n")
//                    TemplateBody(auxMethods, templateBody)
//                }
//                .toList()
//        return Template(auxClasses, imports, templatesBodies)
//    }
//
//    private class Template(
//        val auxClasses: List<Pair<String, String>>,
//        val imports: List<String>,
//        val templates: List<TemplateBody>
//    )
//
//    private data class TemplateBody(
//        val auxMethods: List<String>,
//        val templateBody: String
//    )
//}