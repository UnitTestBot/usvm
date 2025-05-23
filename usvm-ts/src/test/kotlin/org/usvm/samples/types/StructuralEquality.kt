package org.usvm.samples.types

import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class StructuralEquality : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene = loadSampleScene(className, folderPrefix = "types")

    @Test
    fun `test structural equality`() {
        val method = getMethod("Example", "testFunction")
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

                propertyValue.number == 11.0
            }
        )
    }
}