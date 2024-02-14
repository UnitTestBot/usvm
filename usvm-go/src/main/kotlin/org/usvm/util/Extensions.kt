package org.usvm.util

import java.nio.ByteBuffer

val ByteBuffer.bool: Boolean
    get() = this.get() == 1.toByte()

val ByteBuffer.byte: Byte
    get() = this.get()