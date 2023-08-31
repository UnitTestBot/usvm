package org.usvm.algorithms

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.usvm.algorithms.DisjointSets
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisjointSetsTests {
    @Test
    fun testDSU() {
        val subscriber1: (String, String) -> Unit = mockk()
        every { subscriber1(any(), any()) } returns Unit
        val dsu1 = DisjointSets<String>()
        dsu1.subscribe(subscriber1)
        dsu1.union("a", "aa")
        dsu1.union("aaa", "aaaa")
        assertTrue { dsu1.connected("aaa", "aaa") }
        assertFalse { dsu1.connected("aa", "aaaa") }

        dsu1.union("a", "aaaa")
        dsu1.union("aa", "aaaa")
        dsu1.union("b", "bb")
        assertTrue { dsu1.connected("aa", "aaaa") }

        val subscriber2: (String, String) -> Unit = mockk()
        every { subscriber2(any(), any()) } returns Unit
        val dsu2 = dsu1.clone()
        dsu2.subscribe(subscriber2)

        dsu2.union("bb", "bbb")
        dsu2.union("b", "bbb")
        assertFalse { dsu2.connected("a", "b") }

        verify(exactly = 4) { subscriber1(any(), any()) }
        verify(exactly = 1) { subscriber2(any(), any()) }
    }
}