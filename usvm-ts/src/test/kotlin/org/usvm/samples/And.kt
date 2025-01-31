package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClassSignature
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

private fun EtsMethodParameter.toRef(): EtsParameterRef {
    return EtsParameterRef(index, type)
}

class And : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "And.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

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
        // val method = getMethod("And", "andOfNumberAndNumber")
        //
        //   andOfNumberAndNumber(a: number, b: number): number {
        //       if (a && b) return 1
        //       if (a && (b != b)) return 2
        //       if (a) return 3
        //       if ((a != a) && b) return 4
        //       if ((a != a) && (b != b)) return 5
        //       if ((a != a)) return 6
        //       if (b) return 7
        //       if (b != b) return 8
        //       return 9
        //   }
        //

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
        val loc = { EtsInstLocation(method, statements.size) }

        val localA = methodParameters[0].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localB = methodParameters[1].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localThis = EtsLocal("this", EtsClassType(classSignature))

        val assA = EtsAssignStmt(loc(), localA, methodParameters[0].toRef()).also { statements += it }
        val assB = EtsAssignStmt(loc(), localB, methodParameters[1].toRef()).also { statements += it }
        val assThis = EtsAssignStmt(loc(), localThis, EtsThis(EtsClassType(classSignature))).also { statements += it }

        // %0 := a && b
        val local0 = EtsLocal("%0", EtsUnknownType).also { locals += it }
        val ass0 = EtsAssignStmt(loc(), local0, EtsAndExpr(EtsUnknownType, localA, localB)).also { statements += it }
        val if0 = EtsIfStmt(loc(), local0).also { statements += it }
        val ret1 = EtsReturnStmt(loc(), EtsNumberConstant(1.0)).also { statements += it }
        // %1 := (b != b)
        val local1 = EtsLocal("%1", EtsUnknownType).also { locals += it }
        val ass1 = EtsAssignStmt(loc(), local1, EtsNotEqExpr(localB, localB)).also { statements += it }
        // %2 := a && %1 == a && (b != b)
        val local2 = EtsLocal("%2", EtsUnknownType).also { locals += it }
        val ass2 = EtsAssignStmt(loc(), local2, EtsAndExpr(EtsUnknownType, localA, local1)).also { statements += it }
        val if2 = EtsIfStmt(loc(), local2).also { statements += it }
        val ret2 = EtsReturnStmt(loc(), EtsNumberConstant(2.0)).also { statements += it }
        val ifA = EtsIfStmt(loc(), localA).also { statements += it }
        val ret3 = EtsReturnStmt(loc(), EtsNumberConstant(3.0)).also { statements += it }
        // %3 := (a != a)
        val local3 = EtsLocal("%3", EtsUnknownType).also { locals += it }
        val ass3 = EtsAssignStmt(loc(), local3, EtsNotEqExpr(localA, localA)).also { statements += it }
        // %4 := %3 && b == (a != a) && b
        val local4 = EtsLocal("%4", EtsUnknownType).also { locals += it }
        val ass4 = EtsAssignStmt(loc(), local4, EtsAndExpr(EtsUnknownType, local3, localB)).also { statements += it }
        val if4 = EtsIfStmt(loc(), local4).also { statements += it }
        val ret4 = EtsReturnStmt(loc(), EtsNumberConstant(4.0)).also { statements += it }
        // %5 := %3 && %1 == (a != a) && (b != b)
        val local5 = EtsLocal("%5", EtsUnknownType).also { locals += it }
        val ass5 = EtsAssignStmt(loc(), local5, EtsAndExpr(EtsUnknownType, local3, local1)).also { statements += it }
        val if5 = EtsIfStmt(loc(), local5).also { statements += it }
        val ret5 = EtsReturnStmt(loc(), EtsNumberConstant(5.0)).also { statements += it }
        val if3 = EtsIfStmt(loc(), local3).also { statements += it }
        val ret6 = EtsReturnStmt(loc(), EtsNumberConstant(6.0)).also { statements += it }
        val ifB = EtsIfStmt(loc(), localB).also { statements += it }
        val ret7 = EtsReturnStmt(loc(), EtsNumberConstant(7.0)).also { statements += it }
        val if1 = EtsIfStmt(loc(), local1).also { statements += it }
        val ret8 = EtsReturnStmt(loc(), EtsNumberConstant(8.0)).also { statements += it }
        val ret9 = EtsReturnStmt(loc(), EtsNumberConstant(9.0)).also { statements += it }

        // Note: for if-statements, successors must be (falseBranch, trueBranch) !!!
        successorMap[assA] = listOf(assB)
        successorMap[assB] = listOf(assThis)
        successorMap[assThis] = listOf(ass0)
        successorMap[ass0] = listOf(if0)
        successorMap[if0] = listOf(ass1, ret1)
        successorMap[ass1] = listOf(ass2)
        successorMap[ass2] = listOf(if2)
        successorMap[if2] = listOf(ifA, ret2)
        successorMap[ifA] = listOf(ass3, ret3)
        successorMap[ass3] = listOf(ass4)
        successorMap[ass4] = listOf(if4)
        successorMap[if4] = listOf(ass5, ret4)
        successorMap[ass5] = listOf(if5)
        successorMap[if5] = listOf(if3, ret5)
        successorMap[if3] = listOf(ifB, ret6)
        successorMap[ifB] = listOf(if1, ret7)
        successorMap[if1] = listOf(ret9, ret8)

        successorMap[ret1] = emptyList()
        successorMap[ret2] = emptyList()
        successorMap[ret3] = emptyList()
        successorMap[ret4] = emptyList()
        successorMap[ret5] = emptyList()
        successorMap[ret6] = emptyList()
        successorMap[ret7] = emptyList()
        successorMap[ret8] = emptyList()
        successorMap[ret9] = emptyList()

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
        )
    }

    @Test
    fun `test andOfBooleanAndNumber`() {
        // val method = getMethod("And", "andOfBooleanAndNumber")
        //
        //   andOfBooleanAndNumber(a: boolean, b: number): number {
        //       if (a && b) return 1
        //       if (a && (b != b)) return 2
        //       if (a) return 3
        //       if (b) return 4
        //       if (b != b) return 5
        //       return 6
        //   }
        //

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsBooleanType),
            EtsMethodParameter(1, "b", EtsNumberType),
        )
        val locals = mutableListOf<EtsLocal>()

        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfBooleanAndNumber",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
            locals = locals,
        )
        val statements = mutableListOf<EtsStmt>()
        val successorMap = mutableMapOf<EtsStmt, List<EtsStmt>>()
        val loc = { EtsInstLocation(method, statements.size) }

        val localA = methodParameters[0].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localB = methodParameters[1].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localThis = EtsLocal("this", EtsClassType(classSignature))

        val assA = EtsAssignStmt(loc(), localA, methodParameters[0].toRef()).also { statements += it }
        val assB = EtsAssignStmt(loc(), localB, methodParameters[1].toRef()).also { statements += it }
        val assThis = EtsAssignStmt(loc(), localThis, EtsThis(EtsClassType(classSignature))).also { statements += it }

        // %0 := a && b
        val local0 = EtsLocal("%0", EtsUnknownType).also { locals += it }
        val ass0 = EtsAssignStmt(loc(), local0, EtsAndExpr(EtsUnknownType, localA, localB)).also { statements += it }
        val if0 = EtsIfStmt(loc(), local0).also { statements += it }
        val ret1 = EtsReturnStmt(loc(), EtsNumberConstant(1.0)).also { statements += it }
        // %1 := (b != b)
        val local1 = EtsLocal("%1", EtsUnknownType).also { locals += it }
        val ass1 = EtsAssignStmt(loc(), local1, EtsNotEqExpr(localB, localB)).also { statements += it }
        // %2 := a && %1 == a && (b != b)
        val local2 = EtsLocal("%2", EtsUnknownType).also { locals += it }
        val ass2 = EtsAssignStmt(loc(), local2, EtsAndExpr(EtsUnknownType, localA, local1)).also { statements += it }
        val if2 = EtsIfStmt(loc(), local2).also { statements += it }
        val ret2 = EtsReturnStmt(loc(), EtsNumberConstant(2.0)).also { statements += it }
        val ifA = EtsIfStmt(loc(), localA).also { statements += it }
        val ret3 = EtsReturnStmt(loc(), EtsNumberConstant(3.0)).also { statements += it }
        val ifB = EtsIfStmt(loc(), localB).also { statements += it }
        val ret4 = EtsReturnStmt(loc(), EtsNumberConstant(4.0)).also { statements += it }
        val if1 = EtsIfStmt(loc(), local1).also { statements += it }
        val ret5 = EtsReturnStmt(loc(), EtsNumberConstant(5.0)).also { statements += it }
        val ret6 = EtsReturnStmt(loc(), EtsNumberConstant(6.0)).also { statements += it }

        // Note: for if-statements, successors must be (falseBranch, trueBranch) !!!
        successorMap[assA] = listOf(assB)
        successorMap[assB] = listOf(assThis)
        successorMap[assThis] = listOf(ass0)
        successorMap[ass0] = listOf(if0)
        successorMap[if0] = listOf(ass1, ret1)
        successorMap[ass1] = listOf(ass2)
        successorMap[ass2] = listOf(if2)
        successorMap[if2] = listOf(ifA, ret2)
        successorMap[ifA] = listOf(ifB, ret3)
        successorMap[ifB] = listOf(if1, ret4)
        successorMap[if1] = listOf(ret6, ret5)

        successorMap[ret1] = emptyList()
        successorMap[ret2] = emptyList()
        successorMap[ret3] = emptyList()
        successorMap[ret4] = emptyList()
        successorMap[ret5] = emptyList()
        successorMap[ret6] = emptyList()

        method._cfg = EtsCfg(statements, successorMap)
        locals += method.cfg.stmts.filterIsInstance<EtsAssignStmt>().mapNotNull {
            val left = it.lhv
            if (left is EtsLocal) left else null
        }

        discoverProperties<TSObject.TSBoolean, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> a.value && b.number.isNaN() && r.number == 2.0 },
            { a, b, r -> a.value && b.number == 0.0 && r.number == 3.0 },
            { a, b, r -> !a.value && isTruthy(b) && r.number == 4.0 },
            { a, b, r -> !a.value && b.number.isNaN() && r.number == 5.0 },
            { a, b, r -> !a.value && b.number == 0.0 && r.number == 6.0 },
        )
    }

    @Test
    fun `test andOfNumberAndBoolean`() {
        // val method = getMethod("And", "andOfNumberAndBoolean")
        //
        //   andOfNumberAndBoolean(a: number, b: boolean): number {
        //       if (a && b) return 1
        //       if (a) return 2
        //       if ((a != a) && b) return 3.0
        //       if ((a != a) && !b) return 4.0
        //       if (b) return 3
        //       return 4
        //   }
        //

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsNumberType),
            EtsMethodParameter(1, "b", EtsBooleanType),
        )
        val locals = mutableListOf<EtsLocal>()

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
        val loc = { EtsInstLocation(method, statements.size) }

        val localA = methodParameters[0].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localB = methodParameters[1].let { param ->
            EtsLocal(param.name, param.type)
        }
        val localThis = EtsLocal("this", EtsClassType(classSignature))

        val assA = EtsAssignStmt(loc(), localA, methodParameters[0].toRef()).also { statements += it }
        val assB = EtsAssignStmt(loc(), localB, methodParameters[1].toRef()).also { statements += it }
        val assThis = EtsAssignStmt(loc(), localThis, EtsThis(EtsClassType(classSignature))).also { statements += it }

        // %0 := a && b
        val local0 = EtsLocal("%0", EtsUnknownType).also { locals += it }
        val ass0 = EtsAssignStmt(loc(), local0, EtsAndExpr(EtsUnknownType, localA, localB)).also { statements += it }
        val if0 = EtsIfStmt(loc(), local0).also { statements += it }
        val ret1 = EtsReturnStmt(loc(), EtsNumberConstant(1.0)).also { statements += it }
        val ifA = EtsIfStmt(loc(), localA).also { statements += it }
        val ret2 = EtsReturnStmt(loc(), EtsNumberConstant(2.0)).also { statements += it }
        // %1 := (a != a)
        val local1 = EtsLocal("%1", EtsUnknownType).also { locals += it }
        val ass1 = EtsAssignStmt(loc(), local1, EtsNotEqExpr(localA, localA)).also { statements += it }
        // %2 := %1 && b == (a != a) && b
        val local2 = EtsLocal("%2", EtsUnknownType).also { locals += it }
        val ass2 = EtsAssignStmt(loc(), local2, EtsAndExpr(EtsUnknownType, local1, localB)).also { statements += it }
        val if2 = EtsIfStmt(loc(), local2).also { statements += it }
        val ret3 = EtsReturnStmt(loc(), EtsNumberConstant(3.0)).also { statements += it }
        val if1 = EtsIfStmt(loc(), local1).also { statements += it }
        val ret4 = EtsReturnStmt(loc(), EtsNumberConstant(4.0)).also { statements += it }
        val ifB = EtsIfStmt(loc(), localB).also { statements += it }
        val ret5 = EtsReturnStmt(loc(), EtsNumberConstant(5.0)).also { statements += it }
        val ret6 = EtsReturnStmt(loc(), EtsNumberConstant(6.0)).also { statements += it }

        // Note: for if-statements, successors must be (falseBranch, trueBranch) !!!
        successorMap[assA] = listOf(assB)
        successorMap[assB] = listOf(assThis)
        successorMap[assThis] = listOf(ass0)
        successorMap[ass0] = listOf(if0)
        successorMap[if0] = listOf(ifA, ret1)
        successorMap[ifA] = listOf(ass1, ret2)
        successorMap[ass1] = listOf(ass2)
        successorMap[ass2] = listOf(if2)
        successorMap[if2] = listOf(if1, ret3)
        successorMap[if1] = listOf(ifB, ret4)
        successorMap[ifB] = listOf(ret6, ret5)

        successorMap[ret1] = emptyList()
        successorMap[ret2] = emptyList()
        successorMap[ret3] = emptyList()
        successorMap[ret4] = emptyList()
        successorMap[ret5] = emptyList()
        successorMap[ret6] = emptyList()

        method._cfg = EtsCfg(statements, successorMap)
        locals += method.cfg.stmts.filterIsInstance<EtsAssignStmt>().mapNotNull {
            val left = it.lhv
            if (left is EtsLocal) left else null
        }

        discoverProperties<TSObject.TSNumber, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && b.value && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !b.value && r.number == 2.0 },
            { a, b, r -> a.number.isNaN() && b.value && r.number == 3.0 },
            { a, b, r -> a.number.isNaN() && !b.value && r.number == 4.0 },
            { a, b, r -> a.number == 0.0 && b.value && r.number == 5.0 },
            { a, b, r -> a.number == 0.0 && !b.value && r.number == 6.0 },
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
