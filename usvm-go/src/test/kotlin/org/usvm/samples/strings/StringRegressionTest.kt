package org.usvm.samples.strings

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class StringRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "utf8RangeReplacementRune", fixture = "regressions")
    fun utf8RangeReplacementRune() = checkNative(method = "utf8RangeReplacementRune")

    @Test
    @GoSample(method = "utf8RangeRunesAndIndexes", fixture = "regressions")
    fun utf8RangeRunesAndIndexes() = checkNative(method = "utf8RangeRunesAndIndexes")

    @Test
    @GoSample(method = "utf8RangeInvalidBytes", fixture = "regressions")
    fun utf8RangeInvalidBytes() = checkNative(method = "utf8RangeInvalidBytes")

    @Test
    @GoSample(method = "utf8RangeBoundaryRunes", fixture = "regressions")
    fun utf8RangeBoundaryRunes() = checkNative(method = "utf8RangeBoundaryRunes")

    @Test
    @GoSample(method = "utf8RangeMalformedContinuation", fixture = "regressions")
    fun utf8RangeMalformedContinuation() = checkNative(method = "utf8RangeMalformedContinuation")

    @Test
    @GoSample(method = "utf8RangeNamedString", fixture = "regressions")
    fun utf8RangeNamedString() = checkNative(method = "utf8RangeNamedString")

    @Test
    @GoSample(method = "bytesStringCopy", fixture = "regressions")
    fun bytesStringCopy() = checkNative(method = "bytesStringCopy")

    @Test
    @GoSample(method = "emptyStringEquality", fixture = "regressions")
    fun emptyStringEquality() = checkNative(method = "emptyStringEquality")

    @Test
    @GoSample(method = "stringBytesCopy", fixture = "regressions")
    fun stringBytesCopy() = checkNative(method = "stringBytesCopy")

    @Test
    @GoSample(method = "stringConcatenation", fixture = "regressions")
    fun stringConcatenation() = checkNative(method = "stringConcatenation")

    @Test
    @GoSample(method = "stringEquality", fixture = "regressions")
    fun stringEquality() = checkNative(method = "stringEquality")

    @Test
    @GoSample(method = "stringInequality", fixture = "regressions")
    fun stringInequality() = checkNative(method = "stringInequality")

    @Test
    @GoSample(method = "stringLengthDifference", fixture = "regressions")
    fun stringLengthDifference() = checkNative(method = "stringLengthDifference")

    @Test
    @GoSample(method = "stringOrdering", fixture = "regressions")
    fun stringOrdering() = checkNative(method = "stringOrdering")

    @Test
    @GoSample(method = "stringPrefixOrdering", fixture = "regressions")
    fun stringPrefixOrdering() = checkNative(method = "stringPrefixOrdering")

    @Test
    @GoSample(method = "utf8StringLength", fixture = "regressions")
    fun utf8StringLength() = checkNative(method = "utf8StringLength")
}
