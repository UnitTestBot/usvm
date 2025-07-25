package org.usvm.dataflow.ts

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.util.stream.Stream

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, test)

class TestContainerBuilder(var name: String) {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): DynamicContainer = DynamicContainer.dynamicContainer(name, nodes)
}

private fun dynamicContainer(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

class TestFactoryBuilder {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += dynamicContainer(name, init)
    }

    fun build(): Stream<out DynamicNode> = nodes.stream()
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Stream<out DynamicNode> =
    TestFactoryBuilder().apply(init).build()
