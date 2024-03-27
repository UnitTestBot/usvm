package org.usvm.samples

import org.junit.jupiter.api.Test

// TODO rename it
class FirstExamplesTest : PandaMethodTestRunner() {
    @Test
    fun testDataFlowSecurity() {
        discoverProperties(
            Triple("DataFlowSecurity" , "validate", 1),
            emptyArray()
        )
    }

    @Test
    fun testBasicSamples() {
        discoverProperties(
            Triple("BasicSamples" , "add", 0),
            emptyArray()
        )
    }
}
