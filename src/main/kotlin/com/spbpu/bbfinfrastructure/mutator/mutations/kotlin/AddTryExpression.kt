package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.psicreator.util.Factory.tryToCreateExpression
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.getTrue
import com.spbpu.bbfinfrastructure.util.subList
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import kotlin.random.Random
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTryExpression
import kotlin.system.exitProcess

class AddTryExpression : Transformation() {
    override fun transform() {
        for (i in 0 until 200) {
            addTryExpressionAsPsi()
        }
//        for (i in 0 until 200) {
//            addTryExpressionAsText()
//        }
    }

    private fun addTryExpressionAsPsi() {
        val randomNode = file.getAllPSIChildrenOfType<KtExpression>().randomOrNull() ?: return
        val tryBlock = "try {\n${randomNode.text}\n}"
        val catchBlocks = mutableListOf<String>()
        repeat(Random.nextInt(0, 3)) {
            val randomExpressionToInsertInCatch =
                (file.getAllPSIChildrenOfType<KtExpression>().randomOrNull()?.text ?: "")
            val catchBlock = "catch(e: ${listOfRandomExceptions.random()}){\n" +
                    randomExpressionToInsertInCatch +
                    "\n}"
            catchBlocks.add(catchBlock)
        }
        val catchBlocksAsString = catchBlocks.joinToString("\n")
        val finallyBlock =
            if (catchBlocks.isNotEmpty() && Random.getTrue(70)) ""
            else "finally {\n ${file.getAllPSIChildrenOfType<KtExpression>().randomOrNull()?.text ?: ""}\n}"
        val newTryExpression =
            Factory.psiFactory.tryToCreateExpression("$tryBlock\n$catchBlocksAsString\n$finallyBlock") ?: return
        checker.replaceNodeIfPossible(randomNode.node, newTryExpression.node).also { println("RES = $it") }
    }

    private fun addTryExpressionAsText() {
        val fileText = file.text
        val fileBackup = file.copy() as KtFile
        val numOfLinesInLine = fileText.count { it == '\n' }
        val randomPlaceToInsertFrom = Random.nextInt(0, numOfLinesInLine)
        val randomPlaceToInsertTo = Random.nextInt(randomPlaceToInsertFrom, numOfLinesInLine)
        if (randomPlaceToInsertTo - randomPlaceToInsertFrom < 2) return
        val randomTryBlockPlace =
            randomPlaceToInsertFrom to Random.nextInt(randomPlaceToInsertFrom, randomPlaceToInsertTo)
        val randomCatchBlock =
            if (Random.nextBoolean()) 0 to 0
            else randomTryBlockPlace.second to Random.nextInt(randomTryBlockPlace.second, randomPlaceToInsertTo)
        val randomFinallyBlock =
            if (Random.nextBoolean() && randomCatchBlock.first != 0)
                0 to 0
            else if (randomCatchBlock.first != 0)
                randomCatchBlock.second to Random.nextInt(
                    randomCatchBlock.second,
                    randomPlaceToInsertTo
                )
            else
                randomTryBlockPlace.second to Random.nextInt(randomTryBlockPlace.second, randomPlaceToInsertTo)
        val tryBlock =
            "try {\n${getSubtext(randomTryBlockPlace)}\n}\n"
        val catchBlock =
            if (randomCatchBlock.first == 0) ""
            else "catch (e: ${listOfRandomExceptions.random()}) {\n${getSubtext(randomCatchBlock)}\n}\n"
        val finallyBlock =
            if (randomFinallyBlock.first == 0) ""
            else "finally {\n${getSubtext(randomFinallyBlock)}\n}\n"
        val remainText =
            if (randomFinallyBlock.first == 0) getSubtext(randomCatchBlock.second to numOfLinesInLine + 1)
            else getSubtext(randomFinallyBlock.second to numOfLinesInLine + 1)
        val newText =
            "${getSubtext(0 to randomPlaceToInsertFrom)}\n" +
                    tryBlock +
                    catchBlock +
                    finallyBlock +
                    remainText
        val replacementResult = checker.curFile.changePsiFile(newText, checkCorrectness = true, genCtx = false)
        if (replacementResult) {
            if (!checker.checkCompiling()) {
                checker.curFile.changePsiFile(fileBackup, genCtx = false)
            }
        }
    }

    private fun getSubtext(range: Pair<Int, Int>) = fileText.split("\n").subList(range).joinToString("\n")

    private val fileText: String
        get() = file.text
    private val listOfRandomExceptions = listOf("java.lang.Exception", "java.lang.Throwable")
    private val randomConst = Random.nextInt(25, 50)
}