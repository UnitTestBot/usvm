package org.usvm.annotations

import javax.lang.model.type.*
import javax.lang.model.util.SimpleTypeVisitor8

class ConverterToJNITypeDescriptor: SimpleTypeVisitor8<String, Void>() {
    override fun visitPrimitive(t: PrimitiveType, unused: Void?): String {
        val kind = t.kind
        return when (kind) {
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

    override fun visitNoType(t: NoType?, unused: Void?) = "V"

    override fun visitArray(t: ArrayType, unused: Void?): String {
        return "[" + visit(t.componentType)
    }

    override fun visitDeclared(t: DeclaredType, unused: Void?): String {
        return "L" + t.toString().replace('.', '/') + ";"
    }

    override fun visitExecutable(t: ExecutableType, unused: Void?): String {
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