package org.usvm.ps

import org.usvm.statistics.ApplicationGraph

data class Block<Statement>(
    val id: Int,
    var path: MutableList<Statement> = mutableListOf(),

    var parents: MutableSet<Block<Statement>> = mutableSetOf(),
    var children: MutableSet<Block<Statement>> = mutableSetOf()
) {
    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Block<*>

        if (id != other.id) return false

        return true
    }
}

class BlockGraph<Method, Statement>(
    initialStatement: Statement,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
) {
    val root: Block<Statement>
    private var nextBlockId: Int = 0
    private val blockStatementMapping = HashMap<Block<Statement>, MutableList<Statement>>()

    val blocks: Collection<Block<Statement>>
        get() = blockStatementMapping.keys

    init {
        root = buildGraph(initialStatement)
    }

    fun getGraphBlock(statement: Statement): Block<Statement>? {
        blockStatementMapping.forEach {
            if (statement in it.value) {
                return it.key
            }
        }
        return null
    }

    private fun initializeGraphBlockWith(statement: Statement): Block<Statement> {
        val currentBlock = Block(nextBlockId++, path = mutableListOf(statement))
        blockStatementMapping.computeIfAbsent(currentBlock) { mutableListOf() }.add(statement)
        return currentBlock
    }

    private fun createAndLinkWithPreds(statement: Statement): Block<Statement> {
        val currentBlock = initializeGraphBlockWith(statement)
        for (pred in applicationGraph.predecessors(statement)) {
            getGraphBlock(pred)?.children?.add(currentBlock)
            getGraphBlock(pred)?.let { currentBlock.parents.add(it) }
        }
        return currentBlock
    }

    private fun Statement.inBlock() = getGraphBlock(this) != null

    private fun ApplicationGraph<Method, Statement>.filterStmtSuccsNotInBlock(
        statement: Statement,
        forceNewBlock: Boolean
    ): Sequence<Pair<Statement, Boolean>> {
        return this.successors(statement).filter { !it.inBlock() }.map { Pair(it, forceNewBlock) }
    }

    fun buildGraph(initial: Statement): Block<Statement> {
        val root = initializeGraphBlockWith(initial)
        var currentBlock = root
        val statementQueue = ArrayDeque<Pair<Statement, Boolean>>()

        val initialHasMultipleSuccessors = applicationGraph.successors(initial).count() > 1
        statementQueue.addAll(
            applicationGraph.filterStmtSuccsNotInBlock(
                initial,
                forceNewBlock = initialHasMultipleSuccessors
            )
        )

        while (statementQueue.isNotEmpty()) {
            val (currentStatement, forceNew) = statementQueue.removeFirst()

            if (forceNew) {
                // don't need to add `currentStatement` succs, we did it earlier
                createAndLinkWithPreds(currentStatement)
                continue
            }

            // if statement is a call or if statement has multiple successors: next statements start new block
            if (applicationGraph.callees(currentStatement).any() || applicationGraph.successors(currentStatement).count() > 1) {
                currentBlock.path.add(currentStatement)
                blockStatementMapping.computeIfAbsent(currentBlock) { mutableListOf() }.add(currentStatement)
                statementQueue.addAll(applicationGraph.filterStmtSuccsNotInBlock(currentStatement, forceNewBlock = true))
                continue
            }

            // if statement has multiple ins: next statements start new block
            if (applicationGraph.predecessors(currentStatement).count() > 1) {
                currentBlock = createAndLinkWithPreds(currentStatement)
                blockStatementMapping.computeIfAbsent(currentBlock) { mutableListOf() }.add(currentStatement)
                statementQueue.addAll(applicationGraph.filterStmtSuccsNotInBlock(currentStatement, forceNewBlock = true))
                continue
            }

            currentBlock.path.add(currentStatement)
            blockStatementMapping.computeIfAbsent(currentBlock) { mutableListOf() }.add(currentStatement)
            statementQueue.addAll(applicationGraph.filterStmtSuccsNotInBlock(currentStatement, forceNewBlock = false))
        }

        return root
    }

    fun getEdges(): List<GameMapEdge> {
        return blocks.flatMap { block ->
            block.children.map { GameMapEdge(it.id, block.id, GameEdgeLabel(0)) }
        }
    }

    fun getVertices(): Collection<Block<Statement>> = blocks

    fun getBlockFeatures(
        block: Block<Statement>, isCovered: (Statement) -> Boolean,
        inCoverageZone: (Statement) -> Boolean,
        isVisited: (Statement) -> Boolean,
        stateIdsInBlock: List<UInt>
    ): BlockFeatures {
        val firstStatement = block.path.first()
        val lastStatement = block.path.last()
        val visitedByState = isVisited(lastStatement)
        val touchedByState = visitedByState || isVisited(firstStatement)

        return BlockFeatures(
            id = block.id,
            inCoverageZone = inCoverageZone(firstStatement),
            basicBlockSize = block.path.size,
            coveredByTest = isCovered(firstStatement),
            visitedByState = visitedByState,
            touchedByState = touchedByState,
            states = stateIdsInBlock
        )
    }
}
