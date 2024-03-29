package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import com.spbpu.bbfinfrastructure.util.NodeCollector
import com.spbpu.bbfinfrastructure.util.getAllChildrenNodes
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import kotlin.random.Random


class ChangeRandomASTNodesFromAnotherTrees : Transformation() {

    val db = File("database.txt").bufferedReader().lines()

    override fun transform() {
        val randConst = Random.nextInt(numOfTries.first, numOfTries.second)
        val nodes = file.node.getAllChildrenNodes().filter { it.elementType !in NodeCollector.excludes }
        for (i in 0..randConst) {
            print("TRY $i from $randConst")
            val randomNode = nodes[Random.nextInt(0, nodes.size - 1)]
            //Searching nodes of same type in another files
            val line = db
                    .filter { it.takeWhile { it != ' ' } == randomNode.elementType.toString() }.findFirst()
            if (!line.isPresent) continue
            val files = line.get().dropLast(1).takeLastWhile { it != '[' }.split(", ")
            val randomFile = files.random()
            val psi = Factory.psiFactory.createFile(File("lib/testcode/$randomFile").readText())
            val targetNode = psi.node.getAllChildrenNodes().filter { it.elementType == randomNode.elementType }.random()
            //if (targetNode.psi.getAllPSIChildrenOfType<KtNameReferenceExpression>().isNotEmpty()) continue
            if (targetNode.psi is KtConstantExpression) continue
            checker.replaceNodeIfPossible(randomNode, targetNode).also { println( " RES = $it") }
        }
    }

    /*val magicConst = 4*/
    private val numOfTries = if (checker.project.files.size == 1) 500 to 1000 else 2000 to 4000
}