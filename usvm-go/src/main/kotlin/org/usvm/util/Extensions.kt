package org.usvm.util

import org.jacodb.go.api.GoInstLocation
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoNullInst
import java.nio.ByteBuffer

val ByteBuffer.bool: Boolean
    get() = this.get() == 1.toByte()

val ByteBuffer.byte: Byte
    get() = this.get()

fun GoMethod.hasUnsupportedInstructions(): Boolean {
    return blocks.any { it.instructions.any { inst -> inst is GoNullInst } }
}

fun GoMethod.isInit(location: GoInstLocation): Boolean {
    return isInit() && packageName == location.method.packageName
}

fun GoMethod.isInit(): Boolean {
    return name.matches("^init$".toRegex()) // ^init(#\d+)?$ for all inits is not needed because init#1, init#2 and others are called in init()
}

fun GoMethod.isOsInit(): Boolean {
    return name.matches("^osinit$".toRegex())
}
