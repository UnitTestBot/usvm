package org.usvm.dataflow.ts

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.util.stream.Stream

class TestContainerBuilder(var name: String) {
    private val nodes: MutableList<DynamicNode> = mutableListOf()
    private val beforeAllHooks: MutableList<() -> Unit> = mutableListOf()
    private val afterAllHooks: MutableList<() -> Unit> = mutableListOf()

    fun beforeAll(hook: () -> Unit) {
        beforeAllHooks += hook
    }

    fun afterAll(hook: () -> Unit) {
        afterAllHooks += hook
    }

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

      fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): DynamicContainer {
        val allNodes = mutableListOf<DynamicNode>()
        processHooks(beforeAllHooks, "beforeAll")?.let { allNodes += it }
        allNodes += nodes
        processHooks(afterAllHooks, "afterAll")?.let { allNodes += it }
        return DynamicContainer.dynamicContainer(name, allNodes)
    }
}

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, test)

private fun dynamicContainer(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

private fun processHooks(hooks: List<() -> Unit>, prefix: String): DynamicNode? {
    if (hooks.isEmpty()) {
        return null
    }
    if (hooks.size == 1) {
        val hook = hooks.single()
        return dynamicTest(prefix, hook)
    }
    return dynamicContainer(prefix) {
        hooks.forEachIndexed { index, hook ->
            test("$prefix (${index + 1})", hook)
        }
    }
}

class TestFactoryBuilder {
    private val nodes: MutableList<DynamicNode> = mutableListOf()
    private val beforeAllHooks: MutableList<() -> Unit> = mutableListOf()
    private val afterAllHooks: MutableList<() -> Unit> = mutableListOf()

    fun beforeAll(hook: () -> Unit) {
        beforeAllHooks += hook
    }

    fun afterAll(hook: () -> Unit) {
        afterAllHooks += hook
    }

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): Stream<out DynamicNode> {
        val allNodes = mutableListOf<DynamicNode>()
        processHooks(beforeAllHooks, "beforeAll")?.let { allNodes += it }
        allNodes += nodes
        processHooks(afterAllHooks, "afterAll")?.let { allNodes += it }
        return allNodes.stream()
    }
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Stream<out DynamicNode> =
    TestFactoryBuilder().apply(init).build()
