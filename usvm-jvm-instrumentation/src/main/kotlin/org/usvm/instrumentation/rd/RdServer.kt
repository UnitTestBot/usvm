package org.usvm.instrumentation.rd

import com.jetbrains.rd.framework.IProtocol

interface RdServer: Lifetimed {
    val isAlive: Boolean
    val protocol: IProtocol
}
