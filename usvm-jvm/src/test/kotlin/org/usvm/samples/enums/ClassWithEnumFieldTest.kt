package org.usvm.samples.enums

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.enums.ClassWithEnum.StatusEnum
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException
import kotlin.math.abs

class ClassWithEnumFieldTest : JavaMethodTestRunner() {
    @Test
    fun testEnumFieldCode() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithEnumField::getStatusCode,
            ignoreNumberOfAnalysisResults,
            { _, i, r -> abs(i) != 1 && r.isException<NullPointerException>() },
            { _, i, r -> i == -1 && r.getOrThrow() == StatusEnum.READY.code },
            { _, i, r -> i == 1 && r.getOrThrow() == StatusEnum.ERROR.code },
        )
    }
}
