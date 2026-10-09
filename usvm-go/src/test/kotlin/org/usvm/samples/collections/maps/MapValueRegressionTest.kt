package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class MapValueRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "mapStructCopy", fixture = "regressions")
    fun mapStructCopy() = checkNative(method = "mapStructCopy")

    @Test
    @GoSample(method = "mapArrayCopy", fixture = "regressions")
    fun mapArrayCopy() = checkNative(method = "mapArrayCopy")

    @Test
    @GoSample(method = "mapLookupStructCopy", fixture = "regressions")
    fun mapLookupStructCopy() = checkNative(method = "mapLookupStructCopy")

    @Test
    @GoSample(method = "missingStructLookup", fixture = "regressions")
    fun missingStructLookup() = checkNative(method = "missingStructLookup")

    @Test
    @GoSample(method = "missingNamedLookup", fixture = "regressions")
    fun missingNamedLookup() = checkNative(method = "missingNamedLookup")

    @Test
    @GoSample(method = "missingArrayLookup", fixture = "regressions")
    fun missingArrayLookup() = checkNative(method = "missingArrayLookup")

    @Test
    @GoSample(method = "missingStructLookupComma", fixture = "regressions")
    fun missingStructLookupComma() = checkNative(method = "missingStructLookupComma")

    @Test
    @GoSample(method = "namedMapAssertionZero", fixture = "regressions")
    fun namedMapAssertionZero() = checkNative(method = "namedMapAssertionZero")
}
