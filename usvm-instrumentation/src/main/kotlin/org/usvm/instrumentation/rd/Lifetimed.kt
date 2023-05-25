package org.usvm.instrumentation.rd

import com.jetbrains.rd.util.lifetime.Lifetime

interface Lifetimed {
    val lifetime: Lifetime
    fun terminate()
}
