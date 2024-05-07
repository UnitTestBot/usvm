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

    @Test
    fun testSomeOps() {
        discoverProperties(
            Triple("BasicSamples", "someOps", 2),
            emptyArray()
        )
    }

    @Test
    fun testBasicIf() {
        discoverProperties(
            Triple("BasicSamples", "basicIf", 1),
            emptyArray()
        )
    }

    @Test
    fun testMinValue() {
        discoverProperties(
            Triple("MinValue", "findMinValue", 1),
            emptyArray()
        )
    }

    @Test
    fun testPhi() {
        discoverProperties(
            Triple("PhiTest", "foo", 1),
            emptyArray()
        )
    }
}
