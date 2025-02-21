package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class Objects : TsMethodTestRunner() {
    override val scene: EtsScene = run {
        val name = "Objects.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testCreateClassInstance() {
        val method = getMethod("Example", "createClassInstance")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 }
        )
    }

    @Test
    fun testCreateClassInstanceAndWriteField() {
        val method = getMethod("Example", "createClassInstanceAndWriteField")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> (r.properties.toList().single().second as TsValue.TsNumber).number == 14.0 }
        )
    }

    @Test
    fun testCreateClassInstanceAndWriteValueOfAnotherType() {
        val method = getMethod("Example", "createClassInstanceAndWriteValueOfAnotherType")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> r.properties.toList().single().second is TsValue.TsNull }
        )
    }

    @Test
    fun testCreateAnonymousClass() {
        val method = getMethod("Example", "createAnonymousClass")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> (r.properties.toList().single().second as TsValue.TsNumber).number == 15.0 }
        )
    }
}