package org.usvm.ps

import io.github.rchowell.dotlin.digraph
import kotlinx.serialization.Serializable
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.util.escape
import org.usvm.util.log
import java.nio.file.Path
import kotlin.io.path.writeText

class BlockGraph<Method, Statement>(
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, *>,
    initialStatement: Statement,
    private val forkCountsToExit: Map<Statement, UInt>,
    private val minForkCountsToExit: Map<Statement, UInt>
) {
    private val root: Block<Statement>
    private val successorsMap = mutableMapOf<Block<Statement>, List<Statement>>().withDefault { listOf() }
    private val predecessorsMap = mutableMapOf<Block<Statement>, MutableList<Statement>>()
        .withDefault { mutableListOf() }
    private val coveredStatements = mutableMapOf<Statement, Block<Statement>>()
    var currentBlockId = 0
    internal val blockList = mutableListOf<Block<Statement>>()

    init {
        root = buildBlocks(initialStatement)
    }

    private fun chooseNextStatement(statementQueue: ArrayDeque<Statement>): Statement? {
        var currentStatement = statementQueue.removeFirstOrNull()
        while (currentStatement != null && coveredStatements.contains(currentStatement)) {
            currentStatement = statementQueue.removeFirstOrNull()
        }
        return currentStatement
    }

    private fun addSuccessor(block: Block<Statement>, statement: Statement) {
        successorsMap[block] = successorsMap.getValue(block) + statement
    }

    private fun getPredecessors() {
        blockList.forEach { previousBlock ->
            val lastStatement = previousBlock.path.last()
            successors(previousBlock).forEach { nextBlock ->
                predecessorsMap.getValue(nextBlock).add(lastStatement)
            }
        }
    }

    private fun buildBlocks(statement: Statement): Block<Statement> {
        var currentStatement = statement
        val statementQueue = ArrayDeque<Statement>()
        val rootBlock = Block(this)
        var currentBlock = rootBlock
        while (true) {
            if (coveredStatements.contains(currentStatement)) {
                addSuccessor(currentBlock, currentStatement)
                val nextStatement = chooseNextStatement(statementQueue) ?: break
                currentStatement = nextStatement
                currentBlock = Block(this)
                continue
            }
            val predecessors = applicationGraph.predecessors(currentStatement).toList()
            val successors = applicationGraph.successors(currentStatement).toList()
            var newBlock = false
            predecessors.forEach { previousStatement ->
                val previousBlock = coveredStatements[previousStatement]
                if (previousBlock == currentBlock) {
                    return@forEach
                }
                newBlock = true
            }
            if (newBlock && currentBlock.path.isNotEmpty()) {
                addSuccessor(currentBlock, currentStatement)
                currentBlock = Block(this)
            }
            coveredStatements[currentStatement] = currentBlock
            currentBlock.path.add(currentStatement)
            if (successors.size > 1) {
                statementQueue.addAll(successors)
                successors.forEach {
                    addSuccessor(currentBlock, it)
                }
                val nextStatement = chooseNextStatement(statementQueue) ?: break
                currentStatement = nextStatement
                currentBlock = Block(this)
            } else if (successors.isEmpty()) {
                val nextStatement = chooseNextStatement(statementQueue) ?: break
                currentStatement = nextStatement
                currentBlock = Block(this)
            } else {
                currentStatement = successors.first()
            }
        }
        getPredecessors()
        return rootBlock
    }

    private fun predecessors(block: Block<Statement>): List<Block<Statement>> {
        return predecessorsMap.getValue(block).map { coveredStatements[it]!! }
    }

    private fun successors(block: Block<Statement>): List<Block<Statement>> {
        return successorsMap.getValue(block).map { coveredStatements[it]!! }
    }

    fun getEdges(): Pair<List<Int>, List<Int>> {
        return blockList.flatMap { block ->
            predecessors(block).map { Pair(block.id, it.id) }
        }.unzip()
    }

    fun getBlock(statement: Statement): Block<Statement>? {
        return coveredStatements[statement]
    }

    private fun getBlockFeatures(block: Block<Statement>): BlockFeatures {
        val firstStatement = block.path.first()

        val length = block.path.size
        val predecessorsCount = predecessors(block).size
        val successorsCount = successors(block).size
        val totalCalleesCount = block.path.sumOf { applicationGraph.callees(it).count() }
        val forkCountToExit = forkCountsToExit.getValue(firstStatement)
        val minForkCountToExit = minForkCountsToExit.getValue(firstStatement)
        val isCovered = firstStatement !in coverageStatistics.getUncoveredStatements()

        return BlockFeatures(
            length.log(),
            predecessorsCount.log(),
            successorsCount.log(),
            totalCalleesCount.log(),
            forkCountToExit.log(),
            minForkCountToExit.log(),
            if (isCovered) 1.0f else 0.0f,
        )
    }

    fun getGraphFeatures(): List<BlockFeatures> {
        return blockList.map { getBlockFeatures(it) }
    }

    fun saveGraph(filePath: Path) {
        val nodes = mutableListOf<Block<Statement>>()
        val treeQueue = ArrayDeque<Block<Statement>>()
        treeQueue.add(root)
        val visitedBlocks = mutableSetOf<Block<Statement>>()
        visitedBlocks.add(root)
        while (treeQueue.isNotEmpty()) {
            val currentNode = treeQueue.removeFirst()
            nodes.add(currentNode)
            val successors = successors(currentNode)
            treeQueue.addAll(successors.filter { !visitedBlocks.contains(it) })
            visitedBlocks.addAll(successors)
        }
        val graph = digraph("BlockGraph") {
            nodes.forEach { node ->
                val nodeName = node.toString()
                +nodeName
                successors(node).forEach { child ->
                    val childName = child.toString()
                    nodeName - childName
                }
            }
        }
        filePath.parent.toFile().mkdirs()
        filePath.writeText(graph.dot())
    }
}

@Serializable
data class BlockFeatures(
    val logLength: Float = 0.0f,
    val logPredecessorsCount: Float = 0.0f,
    val logSuccessorsCount: Float = 0.0f,
    val logTotalCalleesCount: Float = 0.0f,
    val logForkCountToExit: Float = 0.0f,
    val logMinForkCountToExit: Float = 0.0f,
    val isCovered: Float = 0.0f,
)

data class Block<Statement>(
    val id: Int = 0,
    var path: MutableList<Statement> = mutableListOf()
) {
    constructor(blockGraph: BlockGraph<*, Statement>) : this(
        id = blockGraph.currentBlockId
    ) {
        blockGraph.currentBlockId += 1
        blockGraph.blockList.add(this)
    }

    override fun toString(): String {
        return "\"${id}: ${path.map { it.toString().escape() }}\""
    }
}
