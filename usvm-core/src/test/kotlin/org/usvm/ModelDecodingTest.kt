package org.usvm

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelDecodingTest {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
    }

    @Test
    fun testSmoke() {
        buildDefaultTranslatorAndDecoder<Field, Type, Method>(ctx)
    }
}
