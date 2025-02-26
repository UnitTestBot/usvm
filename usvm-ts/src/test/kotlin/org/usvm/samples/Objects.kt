package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Objects : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test createClassInstance`() {
        val method = getMethod("Example", "createClassInstance")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 }
        )
    }

    @Test
    fun `test createClassInstanceAndWriteField`() {
        val method = getMethod("Example", "createClassInstanceAndWriteField")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> (r.properties.toList().single().second as TsValue.TsNumber).number == 14.0 }
        )
    }

    @Test
    fun `test createClassInstanceAndWriteValueOfAnotherType`() {
        val method = getMethod("Example", "createClassInstanceAndWriteValueOfAnotherType")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> r.properties.toList().single().second is TsValue.TsNull }
        )
    }

    @Test
    fun `test createAnonymousClass`() {
        val method = getMethod("Example", "createAnonymousClass")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r -> (r.properties.toList().single().second as TsValue.TsNumber).number == 15.0 }
        )
    }

    @Test
    fun `test readFieldValue`() {
        val method = getMethod("Example", "readFieldValue")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { x, r -> (x.properties.values.single() as TsValue.TsNumber).number == 1.1 && r.number == 14.0 },
            { x, r -> (x.properties.values.single() as? TsValue.TsNumber)?.number != 1.1 && r.number == 10.0 }
        )
    }
}
