package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy

private fun EtsMethodParameter.toRef(): EtsParameterRef {
    return EtsParameterRef(index, type)
}

class Or : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Or.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

    @Test
    fun `test andOfBooleanAndBoolean`() {
        val method = getMethod("Or", "orOfBooleanAndBoolean")
        discoverProperties<TSObject.TSBoolean, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && b.value && r.number == 1.0 },
            { a, b, r -> a.value && !b.value && r.number == 2.0 },
            { a, b, r -> !a.value && b.value && r.number == 3.0 },
            { a, b, r -> !a.value && !b.value && r.number == 4.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
                { _, _, r -> r.number in 1.0..4.0 },
            )
        )
    }

    @Test
    fun `test andOfNumberAndNumber`() {
        // val method = getMethod("Or", "orOfNumberAndNumber")

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsNumberType),
            EtsMethodParameter(1, "b", EtsNumberType),
        )
        val locals = mutableListOf<EtsLocal>()

        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfNumberAndNumber",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
            locals = locals,
        )
        val statements = mutableListOf<EtsStmt>()
        val successorMap = mutableMapOf<EtsStmt, List<EtsStmt>>()

        // TODO

        method._cfg = EtsCfg(statements, successorMap)
        locals += method.cfg.stmts.filterIsInstance<EtsAssignStmt>().mapNotNull {
            val left = it.lhv
            if (left is EtsLocal) left else null
        }

        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && b.number.isNaN() && r.number == 2.0 },
            { a, b, r -> isTruthy(a) && b.number == 0.0 && r.number == 3.0 },
            { a, b, r -> a.number.isNaN() && isTruthy(b) && r.number == 4.0 },
            { a, b, r -> a.number.isNaN() && b.number.isNaN() && r.number == 5.0 },
            { a, b, r -> a.number.isNaN() && b.number == 0.0 && r.number == 6.0 },
            { a, b, r -> a.number == 0.0 && isTruthy(b) && r.number == 7.0 },
            { a, b, r -> a.number == 0.0 && b.number.isNaN() && r.number == 8.0 },
            { a, b, r -> a.number == 0.0 && b.number == 0.0 && r.number == 9.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
                { _, _, r -> r.number in 1.0..4.0 },
            )
        )
    }
}
