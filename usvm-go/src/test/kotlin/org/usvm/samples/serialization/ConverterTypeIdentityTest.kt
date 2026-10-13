package org.usvm.samples.serialization

import org.jacodb.go.api.BasicType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.junit.jupiter.api.Test
import org.usvm.model.Converter
import org.usvm.model.Package
import org.usvm.model.Type
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ConverterTypeIdentityTest {
    @Test
    fun preservesTupleAndStructFieldTypesAcrossApiNameCollisions() {
        val types = listOf(
            Type.Basic(name = "bool"),
            Type.Basic(name = "int"),
            Type.Basic(name = "rune"),
            Type.Basic(name = "float32"),
            Type.Pointer(name = "*int", elem = "int"),
            Type.Tuple(name = "floatNext", elems = listOf("bool", "float32", "int")),
            Type.Tuple(name = "stringNext", elems = listOf("bool", "int", "rune")),
            Type.Struct(name = "scalarRecord", fields = listOf("int")),
            Type.Struct(name = "pointerRecord", fields = listOf("*int")),
        )

        val actual = Converter.unpack(
            Package(name = "identity", members = emptyList(), types = types.associateBy { it.name }, intSize = 64),
        ).types

        val tuple = assertIs<TupleType>(actual.getValue("stringNext"))
        assertEquals(listOf("bool", "int", "rune"), tuple.types.map { it.typeName })
        assertIs<BasicType>(assertIs<StructType>(actual.getValue("scalarRecord")).fields.orEmpty().single())
        assertIs<PointerType>(assertIs<StructType>(actual.getValue("pointerRecord")).fields.orEmpty().single())
    }

    @Test
    fun preservesRecursiveNamedTypeReferences() {
        val types = listOf(
            Type.Named(name = "node", underlying = "nodeRecord", methods = emptyList()),
            Type.Struct(name = "nodeRecord", fields = listOf("*node")),
            Type.Pointer(name = "*node", elem = "node"),
        )

        val actual = Converter.unpack(
            Package(name = "recursive", members = emptyList(), types = types.associateBy { it.name }, intSize = 64),
        ).types

        val named = assertIs<NamedType>(actual.getValue("node"))
        val pointer = assertIs<PointerType>(assertIs<StructType>(named.underlyingType).fields.orEmpty().single())
        assertSame(named, pointer.baseType)
        assertSame(pointer, actual.getValue("*node"))
    }
}
