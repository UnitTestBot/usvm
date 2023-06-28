package org.usvm.test

import org.junit.jupiter.api.Test
import org.usvm.util.TypesafeMap
import org.usvm.util.add
import org.usvm.util.get
import kotlin.test.assertEquals

class TypesafeMapTests {

    @Test
    fun smokeTest() {
        val map = TypesafeMap()
        map.add(5)
        assertEquals(5, map.get())
    }

    @Test
    fun smokeTest2() {
        val map = TypesafeMap()
        map.add(5)
        val sb = StringBuilder("test")
        map.add(sb)
        assertEquals(sb, map.get())
    }
}
