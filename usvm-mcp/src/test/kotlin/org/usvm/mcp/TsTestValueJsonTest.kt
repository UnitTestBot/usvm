package org.usvm.mcp

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.usvm.api.TsTestValue
import org.usvm.mcp.json.toExceptionDto
import org.usvm.mcp.json.toJson
import kotlin.test.Test
import kotlin.test.assertEquals

class TsTestValueJsonTest {

    @Test
    fun `primitives map to json primitives`() {
        assertEquals(JsonPrimitive(true), TsTestValue.TsBoolean(true).toJson())
        assertEquals(JsonPrimitive(42), TsTestValue.TsNumber.TsInteger(42).toJson())
        assertEquals(JsonPrimitive(3.5), TsTestValue.TsNumber.TsDouble(3.5).toJson())
        assertEquals(JsonPrimitive("hi"), TsTestValue.TsString("hi").toJson())
        assertEquals(JsonNull, TsTestValue.TsNull.toJson())
    }

    @Test
    fun `undefined is a tagged object`() {
        val json = TsTestValue.TsUndefined.toJson().jsonObject
        assertEquals("undefined", json.getValue("\$kind").jsonPrimitive.content)
    }

    @Test
    fun `objects keep class name and properties`() {
        val obj = TsTestValue.TsClass(
            name = "Point",
            properties = mapOf(
                "x" to TsTestValue.TsNumber.TsInteger(1),
                "y" to TsTestValue.TsNumber.TsDouble(2.0),
            ),
        )
        val json = obj.toJson().jsonObject
        assertEquals("object", json.getValue("\$kind").jsonPrimitive.content)
        assertEquals("Point", json.getValue("class").jsonPrimitive.content)
        val props = json.getValue("properties").jsonObject
        assertEquals(JsonPrimitive(1), props.getValue("x"))
        assertEquals(JsonPrimitive(2.0), props.getValue("y"))
    }

    @Test
    fun `arrays are json arrays`() {
        val arr = TsTestValue.TsArray(
            listOf(TsTestValue.TsNumber.TsInteger(1), TsTestValue.TsNumber.TsInteger(2)),
        )
        val json = arr.toJson().jsonArray
        assertEquals(2, json.size)
        assertEquals(JsonPrimitive(1), json[0])
    }

    @Test
    fun `exceptions carry type and message`() {
        val dto = TsTestValue.TsException.StringException("boom").toExceptionDto()
        assertEquals("string", dto.type)
        assertEquals("boom", dto.message)

        val unknown = TsTestValue.TsException.UnknownException.toExceptionDto()
        assertEquals("unknown", unknown.type)
    }
}
