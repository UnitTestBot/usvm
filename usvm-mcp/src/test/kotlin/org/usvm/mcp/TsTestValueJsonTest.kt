package org.usvm.mcp

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.usvm.api.TsParametersState
import org.usvm.api.TsTestValue
import org.usvm.mcp.exec.ResolvedCase
import org.usvm.mcp.json.toExceptionDto
import org.usvm.mcp.json.toJson
import org.usvm.mcp.json.toTestCaseDto
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

    @Test
    fun `cyclic classes retain references independently of sibling inputs`() {
        val properties = mutableMapOf<String, TsTestValue>()
        val cyclic = TsTestValue.TsClass(name = "LinkedNode", properties = properties)
        properties["next"] = cyclic
        val cyclicCase = inputsCase(listOf(cyclic))
        val ordinaryCase = inputsCase(listOf(TsTestValue.TsBoolean(value = true)))
        val cases = listOf(cyclicCase, ordinaryCase)

        val rendered = cases.map { it.toTestCaseDto() }

        assertEquals(expected = "INPUTS", actual = rendered[0].kind)
        val cyclicJson = rendered[0].parameters.single().jsonObject
        val next = cyclicJson.getValue("properties").jsonObject.getValue("next").jsonObject
        assertEquals(expected = cyclicJson.getValue("\$id"), actual = next.getValue("\$ref"))
        assertEquals(expected = "INPUTS", actual = rendered[1].kind)
        assertEquals(expected = listOf(JsonPrimitive(true)), actual = rendered[1].parameters)
    }

    @Test
    fun `aliases across parameters are not emitted as unrelated objects`() {
        val shared = TsTestValue.TsClass(name = "Shared", properties = emptyMap())
        val case = inputsCase(listOf(shared, shared))

        val rendered = case.toTestCaseDto()

        assertEquals(expected = "INPUTS", actual = rendered.kind)
        val first = rendered.parameters[0].jsonObject
        val second = rendered.parameters[1].jsonObject
        assertEquals(expected = first.getValue("\$id"), actual = second.getValue("\$ref"))
    }

    @Test
    fun `equal but distinct objects remain independent input values`() {
        val first = TsTestValue.TsClass(name = "Value", properties = emptyMap())
        val second = TsTestValue.TsClass(name = "Value", properties = emptyMap())
        val case = inputsCase(listOf(first, second))

        val rendered = case.toTestCaseDto()

        assertEquals(expected = "INPUTS", actual = rendered.kind)
        assertEquals(expected = 2, actual = rendered.parameters.size)
    }

    @Test
    fun `cyclic arrays are detected by identity before recursion`() {
        val values = mutableListOf<TsTestValue>()
        val cyclic = TsTestValue.TsArray(values)
        values += cyclic
        val case = inputsCase(listOf(cyclic))

        val rendered = case.toTestCaseDto()

        assertEquals(expected = "INPUTS", actual = rendered.kind)
        val array = rendered.parameters.single().jsonObject
        val self = array.getValue("values").jsonArray.single().jsonObject
        assertEquals(expected = JsonPrimitive("array"), actual = array.getValue("\$kind"))
        assertEquals(expected = array.getValue("\$id"), actual = self.getValue("\$ref"))
    }

    @Test
    fun `reference identifiers do not leak between cases`() {
        val properties = mutableMapOf<String, TsTestValue>()
        val cyclic = TsTestValue.TsClass(name = "LinkedNode", properties = properties)
        properties["next"] = cyclic
        val firstCase = inputsCase(listOf(cyclic))
        val secondCase = inputsCase(listOf(cyclic))

        val first = firstCase.toTestCaseDto().parameters.single().jsonObject
        val second = secondCase.toTestCaseDto().parameters.single().jsonObject

        assertEquals(expected = JsonPrimitive("object"), actual = first.getValue("\$kind"))
        assertEquals(expected = first, actual = second)
    }

    private fun inputsCase(parameters: List<TsTestValue>): ResolvedCase = ResolvedCase(
        test = null,
        error = null,
        inputs = TsParametersState(thisInstance = null, parameters = parameters, globals = emptyMap()),
    )
}
