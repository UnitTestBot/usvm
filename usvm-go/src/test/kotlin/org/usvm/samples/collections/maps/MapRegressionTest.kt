package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class MapRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "mapDeleteLength", fixture = "regressions")
    fun mapDeleteLength() = checkNative(method = "mapDeleteLength")

    @Test
    @GoSample(method = "mapHintLength", fixture = "regressions")
    fun mapHintLength() = checkNative(method = "mapHintLength")

    @Test
    @GoSample(method = "mapInsertLength", fixture = "regressions")
    fun mapInsertLength() = checkNative(method = "mapInsertLength")

    @Test
    @GoSample(method = "mapOverwriteLength", fixture = "regressions")
    fun mapOverwriteLength() = checkNative(method = "mapOverwriteLength")

    @Test
    @GoSample(method = "nilMapLookup", fixture = "regressions")
    fun nilMapLookup() {
        // Report the return/panic mismatch even when the frontend cannot cover the return instruction.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkNative(method = "nilMapLookup")
    }
}
