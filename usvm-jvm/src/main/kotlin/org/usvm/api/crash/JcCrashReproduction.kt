package org.usvm.api.crash

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachine
import kotlin.time.Duration

class JcCrashReproduction(val cp: JcClasspath, timeout: Duration) {
    val options = UMachineOptions(
        solverType = SolverType.YICES,
        timeoutMs = timeout.inWholeMilliseconds,
        stopOnCoverage = -1
    )

    fun reproduceCrash(
        crashException: JcClassOrInterface,
        crashStackTrace: List<CrashStackTraceFrame>
    ): Boolean {
        JcMachine(cp, options).use { machine ->
            TODO("$machine $crashException $crashStackTrace")
        }
    }

    data class CrashStackTraceFrame(
        val method: JcMethod,
        val inst: JcInst
    )
}
