package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.TestKeyInfo
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UContextBv32Size
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.regions.SetRegion
import org.usvm.regions.emptyRegionTree
import kotlin.test.assertTrue

class UpdatesIteratorTest {
    private lateinit var ctx: UContext<USizeSort>
    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContextBv32Size(components)
    }

    @Test
    fun testTreeRegionUpdates() {
        with(ctx) {

            val keyInfo = object : TestKeyInfo<Int, SetRegion<Int>> {
                override fun keyToRegion(key: Int): SetRegion<Int> =
                    if (key != 10) {
                        SetRegion.singleton(key)
                    } else {
                        SetRegion.ofSet(1, 2, 3)
                    }

                override fun eqConcrete(key1: Int, key2: Int): Boolean = key1 == key2
                override fun eqSymbolic(ctx: UContext<*>, key1: Int, key2: Int): UBoolExpr =
                    ctx.mkEq(key1.toBv(), key2.toBv())
            }

            val treeUpdates = UTreeUpdates<Int, SetRegion<Int>, UBv32Sort>(
                emptyRegionTree(),
                keyInfo
            ).write(10, 10.toBv(), guard = mkTrue())
                .write(1, 1.toBv(), guard = mkTrue())
                .write(2, 2.toBv(), guard = mkTrue())
                .write(3, 3.toBv(), guard = mkTrue())

            val iterator = treeUpdates.iterator()
            checkResult(iterator)
        }
    }

    @Test
    fun testFlatUpdatesIterator() = with(ctx) {
        val keyInfo = object : TestKeyInfo<Int, SetRegion<Int>> {}
        val flatUpdates = UFlatUpdates<Int, UBv32Sort>(keyInfo)
            .write(key = 10, value = 10.toBv(), guard = mkTrue())
            .write(key = 1, value = 1.toBv(), guard = mkTrue())
            .write(key = 2, value = 2.toBv(), guard = mkTrue())
            .write(key = 3, value = 3.toBv(), guard = mkTrue())

        val iterator = flatUpdates.iterator()
        checkResult(iterator)
    }

    private fun <Key, ValueSort : USort> UContext<*>.checkResult(
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
            val value = pinpointUpdate.value(key, composer = null)

            assertTrue { key == expectedKeyWithValue.first && value == expectedKeyWithValue.second }
        }
    }
}
