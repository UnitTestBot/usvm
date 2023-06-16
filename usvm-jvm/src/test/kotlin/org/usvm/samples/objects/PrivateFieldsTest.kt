package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class PrivateFieldsTest : JavaMethodTestRunner() {
    @Test
    fun testAccessWithGetter() {
        checkWithExceptionExecutionMatches(
            PrivateFields::accessWithGetter,
            eq(3),
            { _, x, r -> x == null && r.isException<NullPointerException>() },
            { _, x, r -> x.a == 1 && r.getOrNull() == true },
            { _, x, r -> x.a != 1 && r.getOrNull() == false },
        )
    }
}