package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class HiddenFieldExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected UIsExpr in UExprTranslator, that has to be impossible by construction!")
    fun testCheckHiddenField() {
        checkDiscoveredProperties(
            HiddenFieldExample::checkHiddenField,
            eq(4),
            { _, o, _ -> o == null },
            { _, o, r -> o != null && o.a != 1 && r == 2 },
            { _, o, r -> o != null && o.a == 1 && o.b != 2 && r == 2 },
            { _, o, r -> o != null && o.a == 1 && o.b == 2 && r == 1 },
        )
    }

    @Test
    @Disabled("Inherited fields are not supported yet")
    fun testCheckSuccField() {
        checkDiscoveredProperties(
            HiddenFieldExample::checkSuccField,
            eq(5),
            { _, o, _ -> o == null },
            { _, o, r -> o.a == 1 && r == 1 },
            { _, o, r -> o.a != 1 && o.b == 2.0 && r == 2 },
            { _, o, r -> o.a != 1 && o.b != 2.0 && (o as HiddenFieldSuperClass).b == 3 && r == 3 },
            { _, o, r -> o.a != 1 && o.b != 2.0 && (o as HiddenFieldSuperClass).b != 3 && r == 4 },
        )
    }
}