package org.usvm.api.checkers

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcValue
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcContext
import org.usvm.memory.UReadOnlyMemory

sealed interface UCheckResult {
    // Has no data for now
}

interface USatCheckResult : UCheckResult
interface UUnsatCheckResult : UCheckResult
interface UUnknownCheckResult : UCheckResult

interface UCheckersApi {
    val ctx: JcContext
    val memory: UReadOnlyMemory<JcType>

    fun resolveValue(value: JcValue): UExpr<*>?
    fun checkSat(condition: UBoolExpr): UCheckResult
    fun <T> analyze(
        method: JcMethod,
        cp: JcClasspath,
        checkersVisitor: JcInstVisitor<T>,
        targets: List<JcTarget> = emptyList(),
        options: UMachineOptions = UMachineOptions(),
    )

    companion object {
        fun getApi(): UCheckersApi = UCheckersApiImpl()
    }
}
