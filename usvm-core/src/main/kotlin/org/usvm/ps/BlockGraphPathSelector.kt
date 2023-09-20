package org.usvm.ps

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.statistics.*
import java.io.File
import kotlin.io.path.Path

data class GameEdgeLabel(
    val token: Int
)

data class GameMapEdge(
    val vertexFrom: Int,
    val vertexTo: Int,
    val label: GameEdgeLabel,
)

data class BlockFeatures(
    val uid: Int = 0,
    val id: Int,
    val basicBlockSize: Int,
    val inCoverageZone: Boolean,
    val coveredByTest: Boolean,
    val visitedByState: Boolean,
    val touchedByState: Boolean,
    val states: List<UInt>,
)

data class StateHistoryElem(
    val graphVertexId: Int,
    val numOfVisits: Int,
)

data class StateFeatures(
    val id: StateId,
    val position: Int = 0,
    val predictedUsefulness: Int = 42,
    val pathConditionSize: Int,
    val visitedAgainVertices: Int,
    val visitedNotCoveredVerticesInZone: Int,
    val visitedNotCoveredVerticesOutOfZone: Int,
    val history: List<StateHistoryElem>,
    val children: List<UInt>,
)

open class BlockGraphPathSelector<State : UState<*, Method, Statement, *, *, State>, Statement, Method>(
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    val applicationGraph: ApplicationGraph<Method, Statement>
) : UPathSelector<State> {
    protected val states: MutableList<State> = mutableListOf()
    private val visitedStatements = HashSet<Statement>()

    private val filename: String

    protected val blockGraph: BlockGraph<Method, Statement>

    init {
        val method = applicationGraph.methodOf(coverageStatistics.getUncoveredStatements().first())
        filename = method.toString().dropWhile { it != ')' }.drop(1)
        blockGraph = BlockGraph(applicationGraph.entryPoints(method).first(), applicationGraph)
    }

    private fun getNonThrowingLeaves(root: Block<Statement>): Collection<Block<Statement>> {
        val queue = ArrayDeque<Block<Statement>>()
        val visited = HashSet<Block<Statement>>()
        val leaves = mutableListOf<Block<Statement>>()
        queue.addAll(root.children)

        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next.children.isEmpty() && next.path.none { applicationGraph.isThrowing(it) }) {
                leaves.add(next)
            }
            visited.add(next)
            queue.addAll(next.children)
        }

        return leaves
    }

    fun getStateFeatures(state: State): StateFeatures {
        val blockHistory = mutableListOf<Block<Statement>>()

        var lastBlock: Block<Statement> = blockGraph.root
        blockHistory.add(lastBlock)
        for (statement in state.listPath()) {
            if (statement !in lastBlock.path) {
                val someBlockOpt = blockGraph.getGraphBlock(statement)
                // if `statement` already has a block
                if (someBlockOpt != null) {
                    blockHistory.add(someBlockOpt)
                    lastBlock = someBlockOpt
                }
                else {  // encountered non-explored statements, extend block graph
                    val callRoot = blockGraph.buildGraph(statement)
                    val callExitBlocksToConnect = getNonThrowingLeaves(callRoot)

                    // if `statement` is last in prev block -> connect to `lastBlock` children
                    if (statement == lastBlock.path.last() && statement != state.listPath().last()) {
                        lastBlock.children.forEach { lastBlockChild ->
                            callExitBlocksToConnect.forEach { callExitBlock ->
                                callExitBlock.children.add(lastBlockChild)
                            }
                        }
                    } else { // connect to last block itself
                        callExitBlocksToConnect.forEach { externalExitBlock ->
                            externalExitBlock.children.add(lastBlock)
                        }
                    }
                }
            }
        }

        var visitedNotCoveredVerticesInZone = 0
        var visitedNotCoveredVerticesOutOfZone = 0

        for (block in blockHistory.map { block ->
            blockGraph.getBlockFeatures(
                block = block,
                isCovered = ::isCovered,
                inCoverageZone = ::inCoverageZone,
                isVisited = ::isVisited,
                stateIdsInBlock = states.filter { it.currentStatement in block.path }.map { it.id }
            )
        }) {
            if (block.visitedByState && !block.coveredByTest) {
                if (block.inCoverageZone) {
                    visitedNotCoveredVerticesInZone += 1
                } else visitedNotCoveredVerticesOutOfZone += 1
            }
        }

        return StateFeatures(
            id = state.id,
            pathConditionSize = state.pathConstraints.size(),
            visitedAgainVertices = state.listPath().count() - state.listPath().distinct().count(),
            visitedNotCoveredVerticesInZone = visitedNotCoveredVerticesInZone,
            visitedNotCoveredVerticesOutOfZone = visitedNotCoveredVerticesOutOfZone,
            history = blockHistory.map { block ->
                StateHistoryElem(
                    block.id,
                    blockHistory.count { block.id == it.id })
            },
            children = state.pathLocation.accumulatedForks.map { it.id }
        )
    }

    protected fun isCovered(statement: Statement): Boolean {
        return statement in coverageStatistics.getUncoveredStatements()
    }

    protected fun inCoverageZone(statement: Statement): Boolean {
        return coverageStatistics.inCoverageZone(applicationGraph.methodOf(statement))
    }

    protected fun isVisited(statement: Statement) = statement in visitedStatements

    override fun isEmpty(): Boolean {
        return states.isEmpty()
    }

    override fun peek(): State {
        return states.first()
    }

    override fun update(state: State) {}

    override fun add(states: Collection<State>) {
        this.states += states
    }

    override fun remove(state: State) {
        states.remove(state)
    }
}

fun <Type> UPathConstraints<Type, *>.size(): Int {
    return numericConstraints.constraints().count() +
            this.equalityConstraints.distinctReferences.count() +
            this.equalityConstraints.equalReferences.count() +
            this.equalityConstraints.referenceDisequalities.count() +
            this.equalityConstraints.nullableDisequalities.count() +
            this.logicalConstraints.count() +
            this.typeConstraints.symbolicRefToTypeRegion.count()  // TODO: maybe throw out?
}

fun<State : UState<*, *, Statement, *, *, State>, Statement> UState<*, *, Statement, *, *, State>.listPath(): List<Statement> {
    val statements = mutableListOf<Statement>()
    var current: PathsTrieNode<State, Statement>? = this.pathLocation
    while (current !is RootNode && current != null) {
        statements.add(current.statement)
        current = current.parent
    }
    return statements
}
