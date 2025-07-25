package org.usvm.dataflow.ts

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest

class TestContainerBuilder(var name: String) {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): DynamicContainer {
        return DynamicContainer.dynamicContainer(name, nodes.trick())
    }
}

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, test)

private fun dynamicContainer(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

private fun <T> List<T>.trick(): Iterable<T> {
    var i = 0
    return generateSequence { getOrNull(i++) }.asIterable()
}

class TestFactoryBuilder {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): Iterable<DynamicNode> {
        return nodes.trick()
    }
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Iterable<DynamicNode> =
    TestFactoryBuilder().apply(init).build()
