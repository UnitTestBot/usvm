package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.psicreator.util.Factory.psiFactory
import com.spbpu.bbfinfrastructure.util.getAllChildrenNodes
import com.spbpu.bbfinfrastructure.util.replaceThis
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.random.Random

class ChangeRandomASTNodes : Transformation() {

    override fun transform() {
        val numOfSwaps = Random.nextInt(numOfSwaps.first, numOfSwaps.second)
        for (i in 0 until numOfSwaps) {
            val children = file.node.getAllChildrenNodes()
            swapRandomNodes(children, psiFactory)
        }
    }

    companion object {

        fun swapRandomNodes(children: List<ASTNode>, psiFactory: KtPsiFactory, files: List<PsiFile>? = null) {
            //Swap random nodes
            var randomNode1 = children[Random.nextInt(children.size)]
            var randomNode2 = children[Random.nextInt(children.size)]
            while (true) {
                if (randomNode1.text.trim().isEmpty() /*|| randomNode1.text.contains("\n")*/
                    || randomNode1.parents().contains(randomNode2)
                )
                    randomNode1 = children[Random.nextInt(children.size)]
                else if (randomNode2.text.trim().isEmpty() /*|| randomNode2.text.contains("\n")*/
                    || randomNode2.parents().contains(randomNode1)
                )
                    randomNode2 = children[Random.nextInt(children.size)]
                else break
            }
            val new = swap(randomNode1, randomNode2, psiFactory)
            val res = files?.let {
                TODO()
                //checker.checkCompiling(Project.createFromCode(it))
            } ?: checker.checkTextCompiling(file.text)
            if (!res) swap(new.first, new.second, psiFactory)
        }

        private fun swap(randomNode1: ASTNode, randomNode2: ASTNode, psiFactory: KtPsiFactory): Pair<ASTNode, ASTNode> {
            val tmp1 = psiFactory.createProperty("val a = 1")
            val tmp2 = psiFactory.createProperty("val a = 2")
            randomNode1.treeParent.addChild(tmp1.node, randomNode1)
            randomNode2.treeParent.addChild(tmp2.node, randomNode2)
            tmp1.replaceThis(randomNode2.psi)
            tmp2.replaceThis(randomNode1.psi)
            return randomNode2 to randomNode1
        }
    }

    val numOfSwaps = 100 to 1000
}