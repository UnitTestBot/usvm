package org.usvm.annotations

import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.SimpleTypeVisitor8

class ConverterToJNITypeDescriptor : SimpleTypeVisitor8<String, Unit>() {
    override fun visitPrimitive(t: PrimitiveType, unused: Unit?): String {
        return when (t.kind) {
            TypeKind.BOOLEAN -> "Z"
            TypeKind.BYTE -> "B"
            TypeKind.CHAR -> "C"
            TypeKind.SHORT -> "S"
            TypeKind.INT -> "I"
            TypeKind.LONG -> "J"
            TypeKind.FLOAT -> "F"
            TypeKind.DOUBLE -> "D"
            else -> error("Not reachable")
        }
    }

    override fun visitNoType(t: NoType?, unused: Unit?) = "V"

    override fun visitArray(t: ArrayType, unused: Unit?): String {
        return "[" + visit(t.componentType)
    }

    override fun visitDeclared(t: DeclaredType, unused: Unit?): String {
        return "L" + t.toString().replace('.', '/') + ";"
    }

    override fun visitExecutable(t: ExecutableType, unused: Unit?): String {
        val builder = StringBuilder()
        builder.append("(")
        for (param in t.parameterTypes) {
            builder.append(visit(param))
        }
        builder.append(")")
        builder.append(visit(t.returnType))
        return builder.toString()
    }

    fun convert(t: TypeMirror?): String = visit(t)
}
