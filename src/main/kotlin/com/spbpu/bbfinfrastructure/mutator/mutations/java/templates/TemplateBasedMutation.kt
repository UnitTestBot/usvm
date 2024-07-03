package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationLocation
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.random.Random

open class TemplateBasedMutation(private val pathToTemplates: String) : TemplatesInserter() {

    override fun transform() {
        repeat(10_000) {
            val fileBackupText = file.text
//            println("TRY $it")
            try {
                tryToTransform()
            } catch (e: MutationFinishedException) {
                return
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            }
        }
    }

    override fun tryToTransform(): Boolean {
//        val (randomTemplateFile, pathToTemplateFile) = TemplatesDB.getRandomTemplateForPath(pathToTemplates)
//            ?: return false
//        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
//        val insertFromLine = randomPlaceToInsert.getLocationLineNumber()
//        val insertToLine = file.getRandomPlaceToInsertNewLine(insertFromLine)?.getLocationLineNumber() ?: return false
//        val nodesBetween = file.getNodesBetweenWhitespaces(insertFromLine, insertToLine)
//        val parsedTemplates = parseTemplate(randomTemplateFile) ?: return false
//        insertClasses(parsedTemplates)
//        insertImports(parsedTemplates)
//        val (randomTemplate, randomTemplateIndex) = parsedTemplates.templates.randomOrNullWithIndex() ?: return false
//        insertAuxMethods(randomPlaceToInsert, randomTemplate).let { if (!it) return false }
//
//        val randomTemplateBody = randomTemplate.templateBody
//        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
//        val regex = Regex("""~\[(.*?)\]~""")
//        val body = nodesBetween.filter { it.children.isEmpty() }.joinToString("") { it.text }
//        val replacedBody = replaceAssigns(randomTemplateBody, body)
//        val newText = regex.replace(replacedBody) { result ->
//            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
//            val isVar = hole.startsWith("VAR_")
//            val type = if (isVar) hole.substringAfter("VAR_") else hole
//            val capturedType = JavaTypeMappings.mappings[type] ?: type
//            if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
//                ConditionGenerator(scope).generate()?.let { return@replace it }
//            }
//            val isAssign = try {
//                replacedBody.substring(result.groups[0]!!.range.last + 1).let {
//                    it.startsWith(" =") || it.startsWith(" +=") || it.startsWith(" -=")
//                }
//            } catch (e: Throwable) {
//                false
//            }
//            if (isAssign) {
//                nodesBetween.filter { it.children.isEmpty() }.joinToString("") { it.text }
//            } else {
//                val randomValueWithCompatibleType =
//                    if (Random.getTrue(20) || isAssign || isVar) {
//                        if (capturedType == "java.lang.Object") {
//                            scope.randomOrNull()?.name
//                        } else {
//                            scope.filter { it.type == capturedType }
//                                .randomOrNull()?.name
//                        }
//                    } else null
//                if ((isVar) && randomValueWithCompatibleType == null) {
//                    println("CANT FIND VARIABLE OF TYPE $capturedType for assignment")
//                    throw IllegalArgumentException()
//                }
//                randomValueWithCompatibleType
//                    ?: ExpressionGenerator().generateExpressionOfType(scope, capturedType)
//                    ?: throw IllegalArgumentException()
//            }
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
//        val nodeToReplace =
//            nodesBetween
//                .filter { it.parent !in nodesBetween }
//                .mapIndexed { index, psiElement -> if (index > 0) psiElement.delete() else psiElement }
//                .first() as PsiElement
//        nodeToReplace.replaceThis(newPsiBlock)
//        checkNewCode(
//            MutationInfo(
//                mutationName = "TemplateBasedMutation",
//                mutationDescription = "Mutate using template from $pathToTemplateFile with index $randomTemplateIndex",
//                location = MutationLocation(file.name, insertFromLine)
//            )
//        )
        return false
    }

    private fun replaceAssigns(randomTemplateBody: String, newBody: String): String =
        randomTemplateBody
            .split("\n")
            .joinToString("\n") { line ->
                if (Regex("""~\[(.*?)]~ (=|\+=|-=|\*=|/=) .*""").matches(line.trim())) {
                    newBody
                } else {
                    line
                }
            }

}