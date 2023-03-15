package org.usvm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import java.lang.UnsupportedOperationException
import kotlin.test.assertTrue

class UpdatesIteratorTest {
    @Test
    fun testTreeRegionUpdates() {
        with(UContext()) {
            val treeUpdates = UTreeUpdates<Int, SetRegion<Int>, UBv32Sort>(
                emptyRegionTree(),
                { key ->
                    if (key != 10) {
                        SetRegion.singleton(key)
                    } else {
                        SetRegion.ofSet(1, 2, 3)
                    }
                },
                { _, _ -> throw UnsupportedOperationException() },
                { k1, k2 -> mkEq(k1.toBv(), k2.toBv()) },
                { k1, k2 -> k1 == k2 },
                { _, _ -> throw UnsupportedOperationException() }
            ).write(10, 10.toBv(), mkTrue())
                .write(1, 1.toBv(), mkTrue())
                .write(2, 2.toBv(), mkTrue())
                .write(3, 3.toBv(), mkTrue())

            val iterator = treeUpdates.iterator()
            checkResult(iterator)
        }
    }

    @Test
    fun testFlatUpdatesIterator() = with(UContext()) {
        val firstUpdateNode = UPinpointUpdateNode(
            key = 10,
            value = 10.toBv(),
            keyEqualityComparer = { k1, k2 -> mkEq(k1.toBv(), k2.toBv()) }
        )

        val flatUpdates = UFlatUpdates(
            node = firstUpdateNode,
            next = null,
            { _, _ -> throw NotImplementedError() },
            { _, _ -> throw NotImplementedError() },
            { _, _ -> throw NotImplementedError() }
        ).write(key = 1, value = 1.toBv(), mkTrue())
            .write(key = 2, value = 2.toBv(), mkTrue())
            .write(key = 3, value = 3.toBv(), mkTrue())

        val iterator = flatUpdates.iterator()
        checkResult(iterator)
    }

    private fun <Key, ValueSort : USort> UContext.checkResult(
        iterator: Iterator<UUpdateNode<Key, ValueSort>>
    ) {
        val elements = mutableListOf<UUpdateNode<Key, ValueSort>>()

        while (iterator.hasNext()) {
            elements += iterator.next()
        }

        assertThrows<NoSuchElementException> { iterator.next() }

        val expectedValues = listOf(
            10 to 10.toBv(),
            1 to 1.toBv(),
            2 to 2.toBv(),
            3 to 3.toBv()
        )

        assertTrue { elements.size == expectedValues.size }

        elements.zip(expectedValues).forEach { (pinpointUpdate, expectedKeyWithValue) ->
            val key = (pinpointUpdate as UPinpointUpdateNode<Key, ValueSort>).key
            val value = pinpointUpdate.value(key)

            assertTrue { key == expectedKeyWithValue.first && value == expectedKeyWithValue.second }
        }
    }

}