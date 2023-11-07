package org.usvm.ps

import io.github.rchowell.dotlin.digraph
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.usvm.MLConfig
import org.usvm.PathsTrieNode
import org.usvm.RootNode
import org.usvm.UState
import org.usvm.util.escape
import org.usvm.util.getMethodFullName
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.writeText

internal class FeaturesLogger<State : UState<*, *, Statement, *, *, State>, Statement, Method>(
    method: Method,
    blockGraph: BlockGraph<*, Statement>,
    private val mlConfig: MLConfig
) {
    private val filepath = Path(mlConfig.dataPath, "jsons").toString()
    private val filename = getMethodFullName(method)
    private val graphsPath = Path(mlConfig.gameEnvPath, "graphs").toString()
    private val blockGraphsPath = Path(mlConfig.gameEnvPath, "block_graphs").toString()
    private val jsonFormat = Json { encodeDefaults = true }

    init {
        File(filepath).mkdirs()
        blockGraph.saveGraph(Path(blockGraphsPath, filename, "graph.dot"))
    }

    private fun getNodeName(
        node: PathsTrieNode<State, Statement>,
        id: Int,
        extraNodeInfo: (PathsTrieNode<State, Statement>) -> String
    ): String {
        val statement = if (node is RootNode) {
            "No Statement"
        } else {
            node.statement.toString()
        }
        var name = "\"$id: ${statement.escape()}"
        name += extraNodeInfo(node)
        name += "\""
        return name
    }

    fun saveGraph(
        pathsTreeRoot: PathsTrieNode<State, Statement>,
        step: Int,
        extraNodeInfo: (PathsTrieNode<State, Statement>) -> String
    ) {
        val nodes = mutableListOf<PathsTrieNode<State, Statement>>()
        val treeQueue = ArrayDeque<PathsTrieNode<State, Statement>>()
        treeQueue.add(pathsTreeRoot)
        while (treeQueue.isNotEmpty()) {
            val currentNode = treeQueue.removeFirst()
            nodes.add(currentNode)
            treeQueue.addAll(currentNode.children.values)
        }
        val nodeNames = nodes.zip(nodes.indices).associate { (node, id) ->
            Pair(node, getNodeName(node, id, extraNodeInfo))
        }.withDefault { "" }
        val graph = digraph("step$step") {
            nodes.forEach { node ->
                val nodeName = nodeNames.getValue(node)
                +nodeName
                node.children.values.forEach { child ->
                    val childName = nodeNames.getValue(child)
                    nodeName - childName
                }
            }
        }
        val path = Path(graphsPath, filename, "${graph.name}.dot")
        path.parent.toFile().mkdirs()
        path.writeText(graph.dot())
    }

    fun savePath(
        path: List<ActionData>,
        blockGraph: BlockGraph<*, Statement>,
        probabilities: List<List<Float>>,
        statementsCount: Int,
        graphFeaturesList: List<List<BlockFeatures>>,
        getAllFeatures: (StateFeatures, ActionData, Int) -> List<Float>
    ) {
        if (path.isEmpty()) {
            return
        }
        val jsonData = buildJsonObject {
            putJsonArray("path") {
                path.forEach { actionData ->
                    addJsonArray {
                        addJsonArray {
                            actionData.queue.zip(actionData.blockIds).forEach { (stateFeatures, blockId) ->
                                addJsonArray {
                                    getAllFeatures(stateFeatures, actionData, blockId).forEach {
                                        add(it)
                                    }
                                }
                            }
                        }
                        add(actionData.chosenStateId)
                        add(actionData.reward)
                        if (mlConfig.logGraphFeatures) {
                            add(actionData.graphId)
                            addJsonArray {
                                actionData.blockIds.forEach {
                                    add(it)
                                }
                            }
                        }
                    }
                }
            }
            put("statementsCount", statementsCount)
            if (mlConfig.logGraphFeatures) {
                putJsonArray("graphFeatures") {
                    graphFeaturesList.forEach { graphFeatures ->
                        addJsonArray {
                            graphFeatures.forEach { nodeFeatures ->
                                addJsonArray {
                                    jsonFormat.encodeToJsonElement(nodeFeatures).jsonObject.forEach { _, u ->
                                        add(u)
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonArray("graphEdges") {
                    blockGraph.getEdges().toList().forEach { nodeList ->
                        addJsonArray {
                            nodeList.forEach {
                                add(it)
                            }
                        }
                    }
                }
            }
            putJsonArray("probabilities") {
                probabilities.forEach { queueProbabilities ->
                    addJsonArray {
                        queueProbabilities.forEach { probability ->
                            add(probability)
                        }
                    }
                }
            }
        }
        Path(filepath, "$filename.json").toFile()
            .writeText(jsonFormat.encodeToString(jsonData))
    }
}
