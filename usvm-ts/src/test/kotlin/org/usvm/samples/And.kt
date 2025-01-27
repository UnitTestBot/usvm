package org.usvm.samples

import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNotExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy

fun EtsMethodParameter.toRef(): EtsParameterRef {
    return EtsParameterRef(index, type)
}

class And : TSMethodTestRunner() {
    override val scene: EtsScene
        get() = run {
            val name = "And.ts"
            val path = getResourcePath("/samples/$name")
            val file = loadEtsFileAutoConvert(path)
            EtsScene(listOf(file))
        }

    @Test
    fun `test andOfBooleanAndBoolean`() {
        val method = getMethod("And", "andOfBooleanAndBoolean")
        discoverProperties<TSObject.TSBoolean, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && b.value && r.number == 1.0 },
            { a, b, r -> a.value && !b.value && r.number == 2.0 },
            { a, b, r -> !a.value && b.value && r.number == 3.0 },
            { a, b, r -> !a.value && !b.value && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfNumberAndNumber`() {
        val method = getMethod("And", "andOfNumberAndNumber")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !isTruthy(a) && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !isTruthy(a) && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfBooleanAndNumber`() {
        val method = getMethod("And", "andOfBooleanAndNumber")
        discoverProperties<TSObject.TSBoolean, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> a.value && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !a.value && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !a.value && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfNumberAndBoolean`() {
        // val method = getMethod("And", "andOfNumberAndBoolean")
        //
        //    andOfNumberAndBoolean(a: number, b: boolean): number {
        //         if (a && b) return 1
        //         if (a) return 2
        //         if ((a != a) && b) return 3.5
        //         if (b) return 3
        //         if ((a != a) && !b) return 4.5
        //         return 4
        //     }
        //
        val classSignature = EtsClassSignature(
            name = "And",
            file = EtsFileSignature(
                projectName = "test",
                fileName = "And.ts",
            ),
        )
        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsNumberType),
            EtsMethodParameter(1, "b", EtsBooleanType),
        )
        val localA = methodParameters[0].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localB = methodParameters[1].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localThis = EtsLocal("this", EtsClassType(classSignature))
        val locals = mutableListOf(localA, localB, localThis)

        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfNumberAndBoolean",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
            locals = locals,
        )
        val statements = mutableListOf<EtsStmt>()
        val successorMap = mutableMapOf<EtsStmt, List<EtsStmt>>()
        val loc: () -> EtsInstLocation = { EtsInstLocation(method, statements.size) }

        val assA = EtsAssignStmt(loc(), localA, methodParameters[0].toRef()).also { statements += it }
        val assB = EtsAssignStmt(loc(), localB, methodParameters[1].toRef()).also { statements += it }
        val assThis = EtsAssignStmt(loc(), localThis, EtsThis(EtsClassType(classSignature))).also { statements += it }

        // %0 := a && b
        val local0 = EtsLocal("%0", EtsUnknownType).also { locals += it }
        val ass0 = EtsAssignStmt(loc(), local0, EtsAndExpr(EtsUnknownType, localA, localB)).also { statements += it }
        val if1 = EtsIfStmt(loc(), local0).also { statements += it }
        val ret1 = EtsReturnStmt(loc(), EtsNumberConstant(1.0)).also { statements += it }
        val if2 = EtsIfStmt(loc(), localA).also { statements += it }
        val ret2 = EtsReturnStmt(loc(), EtsNumberConstant(2.0)).also { statements += it }
        // %1 := (a != a)
        val local1 = EtsLocal("%1", EtsUnknownType).also { locals += it }
        val ass1 = EtsAssignStmt(loc(), local1, EtsNotEqExpr(localA, localA)).also { statements += it }
        // %2 := %1 && b
        val local2 = EtsLocal("%2", EtsUnknownType).also { locals += it }
        val ass2 = EtsAssignStmt(loc(), local2, EtsAndExpr(EtsUnknownType, local1, localB)).also { statements += it }
        val if3 = EtsIfStmt(loc(), local2).also { statements += it }
        val ret35 = EtsReturnStmt(loc(), EtsNumberConstant(3.5)).also { statements += it }
        val if4 = EtsIfStmt(loc(), localB).also { statements += it }
        val ret3 = EtsReturnStmt(loc(), EtsNumberConstant(3.0)).also { statements += it }
        // %3 := (a != a)
        // Note: we could reuse %1 for (a != a), but here we create a fresh local.
        val local3 = EtsLocal("%3", EtsUnknownType).also { locals += it }
        val ass3 = EtsAssignStmt(loc(), local3, EtsNotEqExpr(localA, localA)).also { statements += it }
        // %4 := !b
        val local4 = EtsLocal("%4", EtsUnknownType).also { locals += it }
        val ass4 = EtsAssignStmt(loc(), local4, EtsNotExpr(localB)).also { statements += it }
        // %5 := %3 && %4
        val local5 = EtsLocal("%5", EtsUnknownType).also { locals += it }
        val ass5 = EtsAssignStmt(loc(), local5, EtsAndExpr(EtsUnknownType, local3, local4)).also { statements += it }
        val if5 = EtsIfStmt(loc(), local5).also { statements += it }
        val ret45 = EtsReturnStmt(loc(), EtsNumberConstant(4.5)).also { statements += it }
        val ret4 = EtsReturnStmt(loc(), EtsNumberConstant(4.0)).also { statements += it }

        // Note: for if-statements, successors must be (falseBranch, trueBranch) !!!
        successorMap[assA] = listOf(assB)
        successorMap[assB] = listOf(assThis)
        successorMap[assThis] = listOf(ass0)
        successorMap[ass0] = listOf(if1)
        successorMap[if1] = listOf(if2, ret1)
        successorMap[if2] = listOf(ass1, ret2)
        successorMap[ass1] = listOf(ass2)
        successorMap[ass2] = listOf(if3)
        successorMap[if3] = listOf(if4, ret35)
        successorMap[if4] = listOf(ass3, ret3)
        successorMap[ass3] = listOf(ass4)
        successorMap[ass4] = listOf(ass5)
        successorMap[ass5] = listOf(if5)
        successorMap[if5] = listOf(ret4, ret45)

        successorMap[ret1] = emptyList()
        successorMap[ret2] = emptyList()
        successorMap[ret35] = emptyList()
        successorMap[ret3] = emptyList()
        successorMap[ret45] = emptyList()
        successorMap[ret4] = emptyList()

        method._cfg = EtsCfg(statements, successorMap)

        discoverProperties<TSObject.TSNumber, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && b.value && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !b.value && r.number == 2.0 },
            { a, b, r -> !a.number.isNaN() && !isTruthy(a) && b.value && r.number == 3.0 },
            { a, b, r -> a.number.isNaN() && b.value && r.number == 3.5 },
            { a, b, r -> !a.number.isNaN() && !isTruthy(a) && !b.value && r.number == 4.0 },
            { a, b, r -> a.number.isNaN() && !isTruthy(a) && !b.value && r.number == 4.5 },
        )
    }

    @Test
    @Disabled("Does not work because objects cannot be null")
    fun `test andOfObjectAndObject`() {
        val method = getMethod("And", "andOfObjectAndObject")
        discoverProperties<TSObject.TSClass, TSObject.TSClass, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !isTruthy(a) && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !isTruthy(a) && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfUnknown`() {
        val method = getMethod("And", "andOfUnknown")
        discoverProperties<TSObject, TSObject, TSObject.TSNumber>(
            method = method,
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && b.value && r.number == 1.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && !b.value && r.number == 2.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && b.value && r.number == 3.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && !b.value && r.number == 4.0
                } else true
            },
        )
    }

    @Test
    fun `test truthyUnknown`() {
        val method = getMethod("And", "truthyUnknown")
        discoverProperties<TSObject, TSObject, TSObject.TSNumber>(
            method = method,
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && !b.value && r.number == 1.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && b.value && r.number == 2.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && !b.value && r.number == 99.0
                } else true
            },
        )
    }
}
