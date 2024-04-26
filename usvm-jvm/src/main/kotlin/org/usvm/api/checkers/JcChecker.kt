package org.usvm.api.checkers

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcValue
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcContext
import org.usvm.machine.JcMachine
import org.usvm.memory.UReadOnlyMemory

sealed interface JcCheckerResult {
    // Has no data for now
}

interface JcCheckerSatResult : JcCheckerResult
interface JcCheckerUnsatResult : JcCheckerResult
interface JcCheckerUnknownResult : JcCheckerResult

interface JcCheckerApi {
    val ctx: JcContext
    val memory: UReadOnlyMemory<JcType>

    fun resolveValue(value: JcValue): UExpr<*>
    fun checkSat(condition: UBoolExpr): JcCheckerResult
}

class JcCheckerRunner(val cp: JcClasspath) {
    private val apiImpl = JcCheckerApiImpl()

    val api: JcCheckerApi
        get() = apiImpl

    fun <T> runChecker(
        entryPoint: JcMethod,
        checkersVisitor: JcInstVisitor<T>,
        targets: List<JcTarget> = emptyList(),
        options: UMachineOptions = UMachineOptions(),
    ) {
        val checkersObserver = JcCheckerObserver(checkersVisitor, apiImpl)

        JcMachine(cp, options, interpreterObserver = checkersObserver).use { machine ->
            machine.analyze(entryPoint, targets)
        }
    }
}
