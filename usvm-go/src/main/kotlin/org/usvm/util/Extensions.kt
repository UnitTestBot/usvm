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
    // Synthetic init#N methods are invoked by the package init method.
    return name == "init"
}

fun GoMethod.isOsInit(): Boolean {
    return name == "osinit"
}
