package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.util.getAllChildrenNodes
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import java.io.File
import kotlin.random.Random

class ReplacePsiNodeToNodeOfSameType: Transformation() {


    override fun transform() {
        val randConst = Random.nextInt(numOfTries.first, numOfTries.second)
        val nodes = file.node.getAllChildrenNodes()
       println("Trying to change some java nodes to nodes from other programs $randConst times")
        for (i in 0..randConst) {
            println("Try №$i of $randConst")
            val randomNode = nodes[Random.nextInt(0, nodes.size - 1)]
            //Searching nodes of same type in another files
            val line = File("databaseJava.txt").bufferedReader().lines()
                .filter { it.takeWhile { it != ' ' } == randomNode.elementType.toString() }.findFirst()
            if (!line.isPresent) continue
            val files = line.get().dropLast(1).takeLastWhile { it != '[' }.split(", ")
            val randomFile =
                if (files.size == 1)
                    files[0]
                else
                    files[Random.nextInt(0, files.size - 1)]
            val psi = PSICreator
                .getPsiForJava(File("${CompilerArgs.javaBaseDir}/$randomFile").readText(), file.project)
            val sameTypeNodes = psi.node.getAllChildrenNodes().filter { it.elementType == randomNode.elementType }
            val targetNode =
                if (sameTypeNodes.size == 1)
                    sameTypeNodes[0]
                else
                    sameTypeNodes[Random.nextInt(0, sameTypeNodes.size - 1)]
            println("TRYING TO REPLACE $targetNode to $randomNode")
            checker.replaceNodeIfPossible(randomNode, targetNode)
        }
    }

    val numOfTries = 10000 to 10003
}