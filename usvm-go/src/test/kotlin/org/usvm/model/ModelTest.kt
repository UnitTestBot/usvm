package org.usvm.model

import org.junit.jupiter.api.Test
import org.usvm.generatedGoFile
import java.io.File
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun testEqualContent() {
        val filename = generatedGoFile("examples/usvm_examples.json").path
        val file = File(filename)

        val parser = Parser()
        val pkg = parser.deserialize(filename)

        val expected = file.readText()
        val actual = parser.serialize(pkg)

        assertEquals(expected, actual)
    }
}
