package org.usvm.dataflow.ts

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.function.Executable
import java.util.stream.Stream

private interface TestProvider {
    fun test(name: String, test: () -> Unit)
}

private interface ContainerProvider {
    fun container(name: String, init: TestContainerBuilder.() -> Unit)
}

class TestContainerBuilder(var name: String) : TestProvider, ContainerProvider {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    override fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    override fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += containerBuilder(name, init)
    }

    fun build(): DynamicContainer = DynamicContainer.dynamicContainer(name, nodes)
}

private fun containerBuilder(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

class TestFactoryBuilder : TestProvider, ContainerProvider {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    override fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    override fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += containerBuilder(name, init)
    }

    fun build(): Stream<out DynamicNode> = nodes.stream()
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Stream<out DynamicNode> =
    TestFactoryBuilder().apply(init).build()

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, Executable(test))
