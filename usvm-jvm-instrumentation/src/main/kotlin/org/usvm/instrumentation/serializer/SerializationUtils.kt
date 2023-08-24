package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.AbstractBuffer
import org.jacodb.api.*
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.findMethodOrNull

fun AbstractBuffer.writeJcMethod(jcMethod: JcMethod) = with(jcMethod) {
    writeString(enclosingClass.name)
    writeString(name)
    writeString(description)
}

fun AbstractBuffer.writeJcClass(jcClass: JcClassOrInterface) = with(jcClass) {
    writeString(name)
}

fun AbstractBuffer.writeJcField(jcField: JcField) = with(jcField) {
    writeString(enclosingClass.name)
    writeString(name)
}

fun AbstractBuffer.writeJcType(jcType: JcType?) {
    val typeName =
        when (jcType) {
            is JcClassType -> jcType.jcClass.name
            is JcTypeVariable -> jcType.jcClass.name
            else -> jcType?.typeName ?: "type_is_null"
        }
    writeString(typeName)
}

fun AbstractBuffer.readJcMethod(jcClasspath: JcClasspath): JcMethod {
    val className = readString()
    val methodName = readString()
    val description = readString()
    return jcClasspath.findClass(className).findMethodOrNull(methodName, description)!!
}

fun AbstractBuffer.readJcClass(jcClasspath: JcClasspath): JcClassOrInterface {
    val className = readString()
    return jcClasspath.findClass(className)
}

fun AbstractBuffer.readJcField(jcClasspath: JcClasspath): JcField {
    val className = readString()
    val fieldName = readString()
    return jcClasspath.findClass(className).findFieldOrNull(fieldName)!!
}

fun AbstractBuffer.readJcType(jcClasspath: JcClasspath): JcType? {
    val typeName = readString()
    if (typeName == "type_is_null") return null
    return jcClasspath.findTypeOrNull(typeName)
}