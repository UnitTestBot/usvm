package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class SliceRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "negativeIndex", fixture = "regressions")
    fun negativeIndex() = checkNative(method = "negativeIndex")

    @Test
    @GoSample(method = "negativeSliceHigh", fixture = "regressions")
    fun negativeSliceHigh() = checkNative(method = "negativeSliceHigh")

    @Test
    @GoSample(method = "nilSliceAppend", fixture = "regressions")
    fun nilSliceAppend() = checkNative(method = "nilSliceAppend")

    @Test
    @GoSample(method = "nilSliceLength", fixture = "regressions")
    fun nilSliceLength() = checkNative(method = "nilSliceLength")

    @Test
    @GoSample(method = "oversizedIndex", fixture = "regressions")
    fun oversizedIndex() = checkNative(method = "oversizedIndex")

    @Test
    @GoSample(method = "sliceAlias", fixture = "regressions")
    fun sliceAlias() = checkNative(method = "sliceAlias")

    @Test
    @GoSample(method = "sliceAppendAllocate", fixture = "regressions")
    fun sliceAppendAllocate() = checkNative(method = "sliceAppendAllocate")

    @Test
    @GoSample(method = "sliceAppendOffset", fixture = "regressions")
    fun sliceAppendOffset() = checkNative(method = "sliceAppendOffset")

    @Test
    @GoSample(method = "sliceAppendReuse", fixture = "regressions")
    fun sliceAppendReuse() = checkNative(method = "sliceAppendReuse")

    @Test
    @GoSample(method = "sliceArrayPointerAlias", fixture = "regressions")
    fun sliceArrayPointerAlias() = checkNative(method = "sliceArrayPointerAlias")

    @Test
    @GoSample(method = "sliceCapacity", fixture = "regressions")
    fun sliceCapacity() = checkNative(method = "sliceCapacity")

    @Test
    @GoSample(method = "sliceCopyOffset", fixture = "regressions")
    fun sliceCopyOffset() = checkNative(method = "sliceCopyOffset")

    @Test
    @GoSample(method = "sliceCopyString", fixture = "regressions")
    fun sliceCopyString() = checkNative(method = "sliceCopyString")

    @Test
    @GoSample(method = "sliceFullCapacity", fixture = "regressions")
    fun sliceFullCapacity() = checkNative(method = "sliceFullCapacity")

    @Test
    @GoSample(method = "sliceOffsetAlias", fixture = "regressions")
    fun sliceOffsetAlias() = checkNative(method = "sliceOffsetAlias")

    @Test
    @GoSample(method = "unsignedSliceLength", fixture = "regressions")
    fun unsignedSliceLength() = checkNative(method = "unsignedSliceLength")
}
