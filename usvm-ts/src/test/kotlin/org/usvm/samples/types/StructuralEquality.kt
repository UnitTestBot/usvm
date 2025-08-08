package org.usvm.samples.types

import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class StructuralEquality : TsMethodTestRunner() {
    private val tsPath = "/samples/types/StructuralEquality.ts"

    override val scene = loadScene(tsPath)

    @Test
    fun `test structural equality`() {
        val method = getMethod("testFunction")
        discoverProperties<TsTestValue.TsClass>(
            method = method,
            { r ->
                val nameCondition = r.name == "D"
                val property = r.properties.values.single()
                val propertyCondition = property is TsTestValue.TsClass && property.name == "A"

                if (!nameCondition || !propertyCondition) {
                    return@discoverProperties false
                }

                val propertyValue = property.properties["x"] as TsTestValue.TsNumber

                propertyValue eq 11
            }
        )
    }
}
