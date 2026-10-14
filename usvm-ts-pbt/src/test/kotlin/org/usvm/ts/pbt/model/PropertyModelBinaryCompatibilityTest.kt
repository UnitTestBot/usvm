package org.usvm.ts.pbt.model

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureDetails
import org.usvm.ts.pbt.backend.PropertyFailureKind
import kotlin.test.assertEquals

class PropertyModelBinaryCompatibilityTest {
    @Test
    fun `entry point retains legacy constructors and default copy calls`() {
        val type = TypeScriptEntryPoint::class.java
        val intType = requireNotNull(Int::class.javaPrimitiveType)
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val constructor = type.getConstructor(
            String::class.java,
            String::class.java,
            ExecutionKind::class.java,
            intType,
            marker,
        )
        val defaultExecutionKindMask = 4
        val enriched = TypeScriptEntryPoint(
            module = "calculator.ts",
            exportName = "predicate",
            checkResultExportName = "outcome",
        )
        val copy = type.getMethod(
            "copy\$default",
            type,
            String::class.java,
            String::class.java,
            ExecutionKind::class.java,
            intType,
            Any::class.java,
        )
        val allLegacyFieldsMask = 7

        val legacy = constructor.newInstance("calculator.ts", "predicate", null, defaultExecutionKindMask, null)
        val copied = copy.invoke(null, enriched, null, null, null, allLegacyFieldsMask, null)

        assertEquals(TypeScriptEntryPoint(module = "calculator.ts", exportName = "predicate"), legacy)
        assertEquals(enriched, copied)
    }

    @Test
    fun `failure details retain legacy constructors and default copy calls`() {
        val type = PropertyFailureDetails::class.java
        val intType = requireNotNull(Int::class.javaPrimitiveType)
        val constructor = type.getConstructor(
            PropertyFailureKind::class.java,
            String::class.java,
            String::class.java,
        )
        val enriched = PropertyFailureDetails(
            kind = PropertyFailureKind.PROPERTY,
            errorName = "NamedCheckFailure",
            message = "Check addition failed",
            checkId = "addition",
            actual = JsConcreteValue.number(1.0),
            expected = JsConcreteValue.number(0.0),
        )
        val copy = type.getMethod(
            "copy\$default",
            type,
            PropertyFailureKind::class.java,
            String::class.java,
            String::class.java,
            intType,
            Any::class.java,
        )
        val allLegacyFieldsMask = 7

        val legacy = constructor.newInstance(PropertyFailureKind.PROPERTY, "Error", "Legacy violation")
        val copied = copy.invoke(null, enriched, null, null, null, allLegacyFieldsMask, null)

        assertEquals(PropertyFailureKind.PROPERTY, legacy.kind)
        assertEquals(null, legacy.checkId)
        assertEquals(enriched, copied)
    }
}
