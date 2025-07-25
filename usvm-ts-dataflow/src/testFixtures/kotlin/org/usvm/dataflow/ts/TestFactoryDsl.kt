package org.usvm.dataflow.ts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest

@DslMarker
annotation class TestFactoryDsl

@TestFactoryDsl
abstract class TestNodeBuilder {
    private val nodeChannel = Channel<() -> DynamicNode>(Channel.UNLIMITED)

    fun test(name: String, test: () -> Unit) {
        nodeChannel.trySend { dynamicTest(name, test) }
    }

    fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodeChannel.trySend { dynamicContainer(name, init) }
    }

    protected fun createNodes(): Iterable<DynamicNode> =
        Iterable { DynamicNodeIterator() }

    private inner class DynamicNodeIterator : Iterator<DynamicNode> {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun hasNext(): Boolean = !nodeChannel.isEmpty

        override fun next(): DynamicNode {
            val node = nodeChannel.tryReceive().getOrThrow()
            return node()
        }
    }
}

class TestContainerBuilder(var name: String) : TestNodeBuilder() {
    fun build(): DynamicContainer {
        return DynamicContainer.dynamicContainer(name, createNodes())
    }
}

class TestFactoryBuilder : TestNodeBuilder() {
    fun build(): Iterable<DynamicNode> {
        return createNodes()
    }
}

inline fun testFactory(init: TestFactoryBuilder.() -> Unit): Iterable<DynamicNode> =
    TestFactoryBuilder().apply(init).build()

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, test)

private fun dynamicContainer(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

inline fun <reified T> TestNodeBuilder.testForEach(
    data: Iterable<T>,
    crossinline nameProvider: (T) -> String = { it.toString() },
    crossinline test: (T) -> Unit,
) {
    data.forEach { item ->
        test(nameProvider(item)) { test(item) }
    }
}

inline fun <reified T> TestNodeBuilder.containerForEach(
    data: Iterable<T>,
    crossinline nameProvider: (T) -> String = { it.toString() },
    crossinline init: TestContainerBuilder.(T) -> Unit,
) {
    data.forEach { item ->
        container(nameProvider(item)) { init(item) }
    }
}
