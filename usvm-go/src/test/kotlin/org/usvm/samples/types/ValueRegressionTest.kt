package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class ValueRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "copiedArrayEquality", fixture = "regressions")
    fun copiedArrayEquality() = checkNative(method = "copiedArrayEquality")

    @Test
    @GoSample(method = "copiedArrayInequality", fixture = "regressions")
    fun copiedArrayInequality() = checkNative(method = "copiedArrayInequality")

    @Test
    @GoSample(method = "copiedStructEquality", fixture = "regressions")
    fun copiedStructEquality() = checkNative(method = "copiedStructEquality")

    @Test
    @GoSample(method = "structPointerFieldsCompareIdentity", fixture = "regressions")
    fun structPointerFieldsCompareIdentity() = checkNative(method = "structPointerFieldsCompareIdentity")

    @Test
    @GoSample(method = "copiedStructSharesPointerIdentity", fixture = "regressions")
    fun copiedStructSharesPointerIdentity() = checkNative(method = "copiedStructSharesPointerIdentity")

    @Test
    @GoSample(method = "interfaceArrayValueEquality", fixture = "regressions")
    fun interfaceArrayValueEquality() = checkNative(method = "interfaceArrayValueEquality")

    @Test
    @GoSample(method = "interfaceNamedTypeInequality", fixture = "regressions")
    fun interfaceNamedTypeInequality() = checkNative(method = "interfaceNamedTypeInequality")

    @Test
    @GoSample(method = "interfaceUncomparablePanics", fixture = "regressions")
    fun interfaceUncomparablePanics() = checkNative(method = "interfaceUncomparablePanics")

    @Test
    @GoSample(method = "interfaceDifferentUncomparableTypes", fixture = "regressions")
    fun interfaceDifferentUncomparableTypes() = checkNative(method = "interfaceDifferentUncomparableTypes")

    @Test
    @GoSample(method = "structEqualityStopsBeforeUncomparableField", fixture = "regressions")
    fun structEqualityStopsBeforeUncomparableField() = checkNative(
        method = "structEqualityStopsBeforeUncomparableField",
    )

    @Test
    @GoSample(method = "structValueCopy", fixture = "regressions")
    fun structValueCopy() = checkNative(method = "structValueCopy")

    @Test
    @GoSample(method = "nestedStructValueCopy", fixture = "regressions")
    fun nestedStructValueCopy() = checkNative(method = "nestedStructValueCopy")

    @Test
    @GoSample(method = "arrayValueCopy", fixture = "regressions")
    fun arrayValueCopy() = checkNative(method = "arrayValueCopy")

    @Test
    @GoSample(method = "structArgumentCopy", fixture = "regressions")
    fun structArgumentCopy() = checkNative(method = "structArgumentCopy")

    @Test
    @GoSample(method = "arrayArgumentCopy", fixture = "regressions")
    fun arrayArgumentCopy() = checkNative(method = "arrayArgumentCopy")

    @Test
    @GoSample(method = "interfaceStructCopy", fixture = "regressions")
    fun interfaceStructCopy() = checkNative(method = "interfaceStructCopy")

    @Test
    @GoSample(method = "nilStructAssertionZero", fixture = "regressions")
    fun nilStructAssertionZero() = checkNative(method = "nilStructAssertionZero")

    @Test
    @GoSample(method = "typedNilPointerAssertion", fixture = "regressions")
    fun typedNilPointerAssertion() = checkNative(method = "typedNilPointerAssertion")

    @Test
    @GoSample(method = "nilStructAssertionOk", fixture = "regressions")
    fun nilStructAssertionOk() = checkNative(method = "nilStructAssertionOk")

    @Test
    @GoSample(method = "pointerToInterfaceDoesNotImplement", fixture = "regressions")
    fun pointerToInterfaceDoesNotImplement() = checkNative(method = "pointerToInterfaceDoesNotImplement")
}
