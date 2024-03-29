package com.spbpu.bbfinfrastructure.util

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.util.getType
import java.io.File
import kotlin.random.Random

object NodeDB {

    fun getRandomFileWithNodesOfType(type: IElementType): KtFile {
        val line = file.filter { it.takeWhile { it != ' ' } == type.toString() }.first()
        val files = line.dropLast(1).takeLastWhile { it != '[' }.split(", ")
        val randomFile = files.random()
        val psi = Factory.psiFactory.createFile(File("${CompilerArgs.baseDir}/$randomFile").readText())
        return psi
    }

    fun getAllNodesOfTypeFromRandomFile(type: IElementType): List<ASTNode> {
        val psi = getRandomFileWithNodesOfType(type)
        return psi.node.getAllChildrenNodes().filter { it.elementType == type }
    }

    fun getAllNodesOfTypeFromRandomFileWithCtx(type: IElementType): Pair<List<ASTNode>, BindingContext> {
        val psi = getRandomFileWithNodesOfType(type)
        val ctx = PSICreator.analyze(psi)
        return psi.node.getAllChildrenNodes().filter { it.elementType == type } to ctx!!
    }

    fun getRandomNodeOfTypeFromRandomFile(type: IElementType): ASTNode = getAllNodesOfTypeFromRandomFile(type).random()
    fun getRandomNodeOfTypeFromRandomFileWithCtx(type: IElementType): Pair<ASTNode, BindingContext> =
        getAllNodesOfTypeFromRandomFileWithCtx(type).let { it.first.random() to it.second }


    private val file = File("database.txt").bufferedReader().lines().toArray().toList().map { it as String }
}