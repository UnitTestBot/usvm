@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException
import java.lang.Boolean

internal class VirtualInvokeExampleTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleVirtualInvoke() {
        checkWithExceptionExecutionMatches(
            VirtualInvokeExample::simpleVirtualInvoke,
            { _, v, r -> v < 0 && r.getOrNull() == -2 },
            { _, v, r -> v == 0 && r.isException<RuntimeException>() },
            { _, v, r -> v > 0 && r.getOrNull() == 1 },
        )
    }

    @Test
    fun testVirtualNative() {
        checkExecutionMatches(
            VirtualInvokeExample::virtualNative,
            { _, r -> r == Boolean::class.java.modifiers }
        )
    }

    @Test
    fun testGetSigners() {
        checkExecutionMatches(
            VirtualInvokeExample::virtualNativeArray,
        )
    }

    @Test
    fun testObjectFromOutside() {
        checkWithExceptionExecutionMatches(
            VirtualInvokeExample::objectFromOutside,
            { _, o, _, r -> o == null && r.isException<NullPointerException>() },
            { _, o, v, r -> o != null && o is VirtualInvokeClassSucc && v < 0 && r.getOrNull() == -1 },
            { _, o, v, r -> o != null && o is VirtualInvokeClassSucc && v == 0 && r.getOrNull() == 0 },
            { _, o, v, r -> o != null && o is VirtualInvokeClassSucc && v > 0 && r.getOrNull() == 1 },
            { _, o, v, r -> o != null && o !is VirtualInvokeClassSucc && v < 0 && r.getOrNull() == 2 },
            { _, o, v, r -> o != null && o !is VirtualInvokeClassSucc && v == 0 && r.isException<RuntimeException>() },
            { _, o, v, r -> o != null && o !is VirtualInvokeClassSucc && v > 0 && r.getOrNull() == 1 },
        )
    }

    @Test
    fun testDoubleCall() {
        checkExecutionMatches(
            VirtualInvokeExample::doubleCall,
            { _, obj, _ -> obj == null },
            { _, obj, r -> obj != null && obj.returnX(obj) == r },
        )
    }

    @Test
    fun testYetAnotherObjectFromOutside() {
        checkWithExceptionExecutionMatches(
            VirtualInvokeExample::yetAnotherObjectFromOutside,
            { _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o !is VirtualInvokeClassSucc && r.getOrNull() == 1 },
            { _, o, r -> o != null && o is VirtualInvokeClassSucc && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testTwoObjects() {
        checkWithExceptionExecutionMatches(
            VirtualInvokeExample::twoObjects,
            { _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o is VirtualInvokeClassSucc && r.getOrNull() == 1 },
            { _, o, r -> o != null && o !is VirtualInvokeClassSucc && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testNestedVirtualInvoke() {
        checkWithExceptionExecutionMatches(
            VirtualInvokeExample::nestedVirtualInvoke,
            { _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o !is VirtualInvokeClassSucc && r.getOrNull() == 1 },
            { _, o, r -> o != null && o is VirtualInvokeClassSucc && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testAbstractClassInstanceFromOutsideWithoutOverrideMethods() {
        checkExecutionMatches(
            VirtualInvokeExample::abstractClassInstanceFromOutsideWithoutOverrideMethods,
            { _, o, _ -> o == null },
            { _, o, r -> o is VirtualInvokeAbstractClassSucc && r == 1 },
        )
    }

    @Test
    fun testAbstractClassInstanceFromOutside() {
        checkExecutionMatches(
            VirtualInvokeExample::abstractClassInstanceFromOutside,
            { _, o, _ -> o == null },
            { _, o, r -> o is VirtualInvokeAbstractClassSucc && r == 2 },
        )
    }

    @Test
    fun testNullValueInReturnValue() {
        checkExecutionMatches(
            VirtualInvokeExample::nullValueInReturnValue,
            { _, o, _ -> o == null },
            { _, o, _ -> o is VirtualInvokeClassSucc },
            { _, o, r -> o is VirtualInvokeClass && r == 10L },
        )
    }

    @Test
    fun testQuasiImplementationInvoke() {
        checkExecutionMatches(
            VirtualInvokeExample::quasiImplementationInvoke,
            { _, result -> result == 0 },
        )
    }
}