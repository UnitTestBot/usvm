package org.usvm.samples

import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.neq
import org.jacodb.ets.dsl.not
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.thisRef
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.isTruthy

class And : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

    @Test
    fun `test andOfBooleanAndBoolean`() {
        val method = getMethod(className, "andOfBooleanAndBoolean")
        discoverProperties<TsValue.TsBoolean, TsValue.TsBoolean, TsValue.TsNumber>(
            method = method,
            { a, b, r -> a.value && b.value && (r.number == 1.0) },
            { a, b, r -> a.value && !b.value && (r.number == 2.0) },
            { a, b, r -> !a.value && b.value && (r.number == 3.0) },
            { a, b, r -> !a.value && !b.value && (r.number == 4.0) },
        )
    }

    @Test
    fun `test andOfBooleanAndBoolean DSL`() {
        val prog = program {
            assign(local("a"), param(0))
            assign(local("b"), param(1))
            assign(local("this"), thisRef())
            ifStmt(and(local("a"), local("b"))) {
                ret(const(1.0))
            }
            ifStmt(local("a")) {
                ret(const(2.0))
            }
            ifStmt(local("b")) {
                ret(const(3.0))
            }
            ret(const(4.0))
        }
        val blockCfg = prog.toBlockCfg()

        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfBooleanAndBoolean",
                parameters = listOf(
                    EtsMethodParameter(0, "a", EtsBooleanType),
                    EtsMethodParameter(1, "b", EtsBooleanType),
                ),
                returnType = EtsNumberType,
            ),
        )
        method.enclosingClass = scene.projectClasses.first { it.name == DEFAULT_ARK_CLASS_NAME }

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        method._cfg = etsBlockCfg

        discoverProperties<TsValue.TsBoolean, TsValue.TsBoolean, TsValue.TsNumber>(
            method = method,
            { a, b, r -> a.value && b.value && (r.number == 1.0) },
            { a, b, r -> a.value && !b.value && (r.number == 2.0) },
            { a, b, r -> !a.value && b.value && (r.number == 3.0) },
            { a, b, r -> !a.value && !b.value && (r.number == 4.0) },
        )
    }

    @Test
    fun `test andOfNumberAndNumber`() {
        // val method = getMethod(className, "andOfNumberAndNumber")
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

        val prog = program {
            assign(local("a"), param(0))
            assign(local("b"), param(1))
            assign(local("this"), thisRef())
            ifStmt(and(local("a"), local("b"))) {
                ret(const(1.0))
            }
            ifStmt(and(local("a"), neq(local("b"), local("b")))) {
                ret(const(2.0))
            }
            ifStmt(local("a")) {
                ret(const(3.0))
            }
            ifStmt(and(neq(local("a"), local("a")), local("b"))) {
                ret(const(4.0))
            }
            ifStmt(and(neq(local("a"), local("a")), neq(local("b"), local("b")))) {
                ret(const(5.0))
            }
            ifStmt(neq(local("a"), local("a"))) {
                ret(const(6.0))
            }
            ifStmt(local("b")) {
                ret(const(7.0))
            }
            ifStmt(neq(local("b"), local("b"))) {
                ret(const(8.0))
            }
            ret(const(9.0))
        }
        val blockCfg = prog.toBlockCfg()

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsNumberType),
            EtsMethodParameter(1, "b", EtsNumberType),
        )
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfNumberAndNumber",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
        )
        method.enclosingClass = scene.projectClasses.first { it.name == DEFAULT_ARK_CLASS_NAME }

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        method._cfg = etsBlockCfg

        discoverProperties<TsValue.TsNumber, TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && (r.number == 1.0) },
            { a, b, r -> isTruthy(a) && b.number.isNaN() && (r.number == 2.0) },
            { a, b, r -> isTruthy(a) && (b.number == 0.0) && (r.number == 3.0) },
            { a, b, r -> a.number.isNaN() && isTruthy(b) && (r.number == 4.0) },
            { a, b, r -> a.number.isNaN() && b.number.isNaN() && (r.number == 5.0) },
            { a, b, r -> a.number.isNaN() && (b.number == 0.0) && (r.number == 6.0) },
            { a, b, r -> (a.number == 0.0) && isTruthy(b) && (r.number == 7.0) },
            { a, b, r -> (a.number == 0.0) && b.number.isNaN() && (r.number == 8.0) },
            { a, b, r -> (a.number == 0.0) && (b.number == 0.0) && (r.number == 9.0) },
        )
    }

    @Test
    fun `test andOfBooleanAndNumber`() {
        // val method = getMethod(className, "andOfBooleanAndNumber")
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

        val prog = program {
            assign(local("a"), param(0))
            assign(local("b"), param(1))
            assign(local("this"), thisRef())
            ifStmt(and(local("a"), local("b"))) {
                ret(const(1.0))
            }
            ifStmt(and(local("a"), neq(local("b"), local("b")))) {
                ret(const(2.0))
            }
            ifStmt(local("a")) {
                ret(const(3.0))
            }
            ifStmt(local("b")) {
                ret(const(4.0))
            }
            ifStmt(neq(local("b"), local("b"))) {
                ret(const(5.0))
            }
            ret(const(6.0))
        }
        val blockCfg = prog.toBlockCfg()

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsBooleanType),
            EtsMethodParameter(1, "b", EtsNumberType),
        )
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfBooleanAndNumber",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
        )
        method.enclosingClass = scene.projectClasses.first { it.name == DEFAULT_ARK_CLASS_NAME }

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        method._cfg = etsBlockCfg

        discoverProperties<TsValue.TsBoolean, TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { a, b, r -> a.value && isTruthy(b) && (r.number == 1.0) },
            { a, b, r -> a.value && b.number.isNaN() && (r.number == 2.0) },
            { a, b, r -> a.value && (b.number == 0.0) && (r.number == 3.0) },
            { a, b, r -> !a.value && isTruthy(b) && (r.number == 4.0) },
            { a, b, r -> !a.value && b.number.isNaN() && (r.number == 5.0) },
            { a, b, r -> !a.value && (b.number == 0.0) && (r.number == 6.0) },
        )
    }

    @Test
    fun `test andOfNumberAndBoolean`() {
        // val method = getMethod(className, "andOfNumberAndBoolean")
        //
        //   andOfNumberAndBoolean(a: number, b: boolean): number {
        //       if (a && b) return 1
        //       if (a) return 2
        //       if ((a != a) && b) return 3.0
        //       if ((a != a) && !b) return 4.0
        //       if (b) return 5
        //       return 6
        //   }
        //

        val prog = program {
            assign(local("a"), param(0))
            assign(local("b"), param(1))
            assign(local("this"), thisRef())
            ifStmt(and(local("a"), local("b"))) {
                ret(const(1.0))
            }
            ifStmt(local("a")) {
                ret(const(2.0))
            }
            ifStmt(and(neq(local("a"), local("a")), local("b"))) {
                ret(const(3.0))
            }
            ifStmt(and(neq(local("a"), local("a")), not(local("b")))) {
                ret(const(4.0))
            }
            ifStmt(local("b")) {
                ret(const(5.0))
            }
            ret(const(6.0))
        }
        val blockCfg = prog.toBlockCfg()

        val methodParameters = listOf(
            EtsMethodParameter(0, "a", EtsNumberType),
            EtsMethodParameter(1, "b", EtsBooleanType),
        )
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "andOfNumberAndBoolean",
                parameters = methodParameters,
                returnType = EtsNumberType,
            ),
        )
        method.enclosingClass = scene.projectClasses.first { it.name == DEFAULT_ARK_CLASS_NAME }

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        method._cfg = etsBlockCfg

        discoverProperties<TsValue.TsNumber, TsValue.TsBoolean, TsValue.TsNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && b.value && (r.number == 1.0) },
            { a, b, r -> isTruthy(a) && !b.value && (r.number == 2.0) },
            { a, b, r -> a.number.isNaN() && b.value && (r.number == 3.0) },
            { a, b, r -> a.number.isNaN() && !b.value && (r.number == 4.0) },
            { a, b, r -> (a.number == 0.0) && b.value && (r.number == 5.0) },
            { a, b, r -> (a.number == 0.0) && !b.value && (r.number == 6.0) },
        )
    }

    @Test
    @Disabled("Does not work because objects cannot be null")
    fun `test andOfObjectAndObject`() {
        val method = getMethod(className, "andOfObjectAndObject")
        discoverProperties<TsValue.TsClass, TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && (r.number == 1.0) },
            { a, b, r -> isTruthy(a) && !isTruthy(b) && (r.number == 2.0) },
            { a, b, r -> !isTruthy(a) && isTruthy(b) && (r.number == 3.0) },
            { a, b, r -> !isTruthy(a) && !isTruthy(b) && (r.number == 4.0) },
        )
    }

    @Test
    fun `test andOfUnknown`() {
        val method = getMethod(className, "andOfUnknown")
        discoverProperties<TsValue, TsValue, TsValue.TsNumber>(
            method = method,
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    a.value && b.value && (r.number == 1.0)
                } else true
            },
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    a.value && !b.value && (r.number == 2.0)
                } else true
            },
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    !a.value && b.value && (r.number == 3.0)
                } else true
            },
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    !a.value && !b.value && (r.number == 4.0)
                } else true
            },
        )
    }

    @Test
    fun `test truthyUnknown`() {
        val method = getMethod(className, "truthyUnknown")
        discoverProperties<TsValue, TsValue, TsValue.TsNumber>(
            method = method,
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    a.value && !b.value && (r.number == 1.0)
                } else true
            },
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    !a.value && b.value && (r.number == 2.0)
                } else true
            },
            { a, b, r ->
                if (a is TsValue.TsBoolean && b is TsValue.TsBoolean) {
                    !a.value && !b.value && (r.number == 99.0)
                } else true
            },
        )
    }
}
