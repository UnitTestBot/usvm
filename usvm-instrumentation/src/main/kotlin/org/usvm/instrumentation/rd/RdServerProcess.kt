package org.usvm.instrumentation.rd

import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import org.usvm.instrumentation.generated.models.InstrumentedProcessModel

class RdServerProcess(
    private val process: Process,
    override val lifetime: LifetimeDefinition,
    override val protocol: Protocol,
    val model: InstrumentedProcessModel
) : RdServer {
    override val isAlive: Boolean
        get() = lifetime.isAlive && process.isAlive

    override fun terminate() {
        lifetime.terminate()
    }
}
