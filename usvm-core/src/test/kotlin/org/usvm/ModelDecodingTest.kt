package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentMapOf

class ModelDecodingTest {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
    }

}
