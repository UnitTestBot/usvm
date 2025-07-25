package org.usvm.dataflow.ts

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.util.stream.Stream

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, test)

class TestContainerBuilder(var name: String) {
    private val nodes: MutableList<DynamicNode> = mutableListOf()
    private var built: Boolean = false

    fun test(name: String, test: () -> Unit) {
        // Note: if you see this error, you probably have a nested test,
        //       e.g. `test("A") { test("A-nested") { ... } }`,
        //       which is not supported.
        check(!built) { "Container '${this.name}' has already been built and cannot be modified." }
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        check(!built) { "Container '${this.name}' has already been built and cannot be modified." }
        nodes += dynamicContainer(name, init)
    }

    fun build(): DynamicContainer {
        check(!built) { "Container '$name' has already been built." }
        built = true
        return DynamicContainer.dynamicContainer(name, nodes)
    }
}

private fun dynamicContainer(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

class TestFactoryBuilder {
    private val nodes: MutableList<DynamicNode> = mutableListOf()
    private var built: Boolean = false

    fun test(name: String, test: () -> Unit) {
        check(!built) { "TestFactory has already been built and cannot be modified." }
        nodes += dynamicTest(name, test)
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        check(!built) { "TestFactory has already been built and cannot be modified." }
        nodes += dynamicContainer(name, init)
    }

    fun build(): Stream<out DynamicNode> {
        check(!built) { "TestFactory has already been built." }
        built = true
        return nodes.stream()
    }
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Stream<out DynamicNode> =
    TestFactoryBuilder().apply(init).build()
