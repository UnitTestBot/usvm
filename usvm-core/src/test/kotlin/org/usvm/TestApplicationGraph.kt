package org.usvm

import org.usvm.statistics.ApplicationGraph

internal data class TestInstruction(val method: String, val offset: Int) {
    constructor(offset: Int) : this("", offset)
}

internal interface TestMethodGraphBuilder {
    fun entryPoint(offset: Int)
    fun exitPoint(offset: Int)
    fun edge(from: Int, to: Int)
    fun bidirectionalEdge(from: Int, to: Int)
    fun call(offset: Int, callee: String)
}

internal interface TestApplicationGraphBuilder {
    fun method(name: String, instructionsCount: Int, init: TestMethodGraphBuilder.() -> Unit)
}

private class TestMethodGraphBuilderImpl(val name: String, instructionsCount: Int) : TestMethodGraphBuilder {
    val adjacencyLists = Array(instructionsCount) { mutableSetOf(it) }
    val calleesByOffset = mutableMapOf<Int, String>()
    val offsetsByCallee = mutableMapOf<String, MutableList<Int>>()
    val entryPoints = mutableSetOf<Int>()
    val exitPoints = mutableSetOf<Int>()

    override fun entryPoint(offset: Int) {
        entryPoints.add(offset)
    }

    override fun exitPoint(offset: Int) {
        exitPoints.add(offset)
    }

    override fun edge(from: Int, to: Int) {
        adjacencyLists[from].add(to)
    }

    override fun bidirectionalEdge(from: Int, to: Int) {
        adjacencyLists[from].add(to)
        adjacencyLists[to].add(from)
    }

    override fun call(offset: Int, callee: String) {
        calleesByOffset[offset] = callee
        offsetsByCallee.computeIfAbsent(callee) { mutableListOf() }.add(offset)
    }
}

private class TestApplicationGraphBuilderImpl : TestApplicationGraphBuilder, ApplicationGraph<String, TestInstruction> {
    private val methodBuilders = mutableMapOf<String, TestMethodGraphBuilderImpl>()

    override fun method(name: String, instructionsCount: Int, init: TestMethodGraphBuilder.() -> Unit) {
        val builder = TestMethodGraphBuilderImpl(name, instructionsCount)
        init(builder)
        methodBuilders[name] = builder
    }

    override fun predecessors(node: TestInstruction): Sequence<TestInstruction> {
        val builder = methodBuilders.getValue(node.method)
        val predecessors = mutableListOf<TestInstruction>()
        for (i in builder.adjacencyLists.indices) {
            if (builder.adjacencyLists[i].contains(node.offset)) {
                predecessors.add(TestInstruction(node.method, i))
            }
        }
        return predecessors.asSequence()
    }

    override fun successors(node: TestInstruction): Sequence<TestInstruction> {
        return methodBuilders
            .getValue(node.method)
            .adjacencyLists[node.offset]
            .map { TestInstruction(node.method, it) }
            .asSequence()
    }

    override fun callees(node: TestInstruction): Sequence<String> {
        val builder = methodBuilders.getValue(node.method)
        return builder.calleesByOffset[node.offset]?.let { sequenceOf(it) } ?: emptySequence()
    }

    override fun callers(method: String): Sequence<TestInstruction> {
        return methodBuilders
            .mapNotNull { m -> m.value.offsetsByCallee[method]?.map { TestInstruction(m.value.name, it) } }
            .flatten()
            .asSequence()
    }

    override fun entryPoints(method: String): Sequence<TestInstruction> {
        return methodBuilders.getValue(method).entryPoints.map { TestInstruction(method, it) }.asSequence()
    }

    override fun exitPoints(method: String): Sequence<TestInstruction> {
        return methodBuilders.getValue(method).exitPoints.map { TestInstruction(method, it) }.asSequence()
    }

    override fun methodOf(node: TestInstruction): String = node.method

    override fun statementsOf(method: String): Sequence<TestInstruction> {
        return methodBuilders
            .getValue(method)
            .adjacencyLists
            .indices
            .map { TestInstruction(method, it) }
            .asSequence()
    }
}

internal fun appGraph(init: TestApplicationGraphBuilder.() -> Unit): ApplicationGraph<String, TestInstruction> {
    val builder = TestApplicationGraphBuilderImpl()
    init(builder)
    return builder
}
