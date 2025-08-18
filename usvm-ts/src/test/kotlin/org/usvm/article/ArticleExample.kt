package org.usvm.article

import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.not
import org.jacodb.ets.dsl.param
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.buildEtsMethod
import org.usvm.util.getResourcePath
import org.usvm.util.toDouble
import kotlin.time.Duration

class ArticleExample {
    val scene = run {
        val name = "examples.ts"
        val path = getResourcePath("/article/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }
    val options = UMachineOptions(timeout = Duration.INFINITE)
    val tsOptions = TsOptions(checkFieldPresents = true, enableVisualization = true)

    private fun formatTests(tests: List<TsTest>): String {
        return tests.mapIndexed { idx, t ->
            val prefix = "  ${idx + 1}) "
            val indent = " ".repeat(prefix.length)
            val lines = t.toString().lineSequence().toList()
            when {
                lines.isEmpty() -> prefix.trimEnd()
                else -> buildString {
                    appendLine(prefix + lines.first())
                    lines.drop(1).forEach { appendLine(indent + it) }
                }.trimEnd()
            }
        }.joinToString("\n")
    }

    private fun generateTestsFor(methodName: String): List<TsTest> {
        val machine = TsMachine(scene, options, tsOptions)
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == methodName }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        return tests
    }

    @Test
    fun runF1() {
        /**
         *     f1(o: any) {
         *       if ("x" in o && typeof o.x === "number") return 1;
         *       return 2;
         *     }
         *
         */

        val methodName = "f1"
        val method = buildEtsMethod(
            name = methodName,
            enclosingClass = scene.projectClasses.first(),
            parameters = listOf(
                "o" to EtsAnyType
            ),
            returnType = EtsNumberType,
        ) {
            // o := arg(0)
            val o = local("o")
            assign(o, param(0))

            val inExpr = CustomValue {
                EtsInExpr(EtsStringConstant("x"), EtsLocal("o", EtsUnknownType))
            }
            val typeOfExpr = CustomValue {
                EtsTypeOfExpr(
                    EtsInstanceFieldRef(
                        EtsLocal("o", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "x", EtsUnknownType),
                        EtsUnknownType
                    )
                )
            }
            ifStmt(and(inExpr, eqq(typeOfExpr, const("number")))) {
                ret(const(1))
            }

            ret(const(2))
        }

        // Generate tests using the built method
        val machine = TsMachine(scene, options, tsOptions)
        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }

        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        // Basic checks for generated tests
        check(tests.isNotEmpty()) { "Expected at least 1 test for f1, got ${tests.size}" }

        // Check that we have tests for both branches
        val returnOne = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        val returnTwo = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }

        check(returnOne != null && returnTwo != null)
    }

    @Test
    fun runF2() {
        /**
         *     // f2: throws if "x" is missing
         *     f2(o: any) {
         *       if (!("x" in o)) return 1;
         *       return 2;
         *     }
         */
        val methodName = "f2"
        val method = buildEtsMethod(
            name = methodName,
            enclosingClass = scene.projectClasses.first(),
            parameters = listOf(
                "o" to EtsAnyType
            ),
            returnType = EtsNumberType,
        ) {
            // o := arg(0)
            val o = local("o")
            assign(o, param(0))

            val inExpr = CustomValue {
                EtsInExpr(EtsStringConstant("x"), EtsLocal("o", EtsUnknownType))
            }
            ifStmt(not(inExpr)) {
                ret(const(1))
            }
            ret(const(2))
        }

        // Generate tests using the built method
        val machine = TsMachine(scene, options, tsOptions)
        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }

        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        check(tests.isNotEmpty()) { "Expected at least 1 test for f2, got ${tests.size}" }

        val testWithOne = tests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        val testWithTwo = tests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }

        val firstParameter = (testWithOne.before.parameters.singleOrNull() as? TsTestValue.TsClass)?.properties
        check(firstParameter == null || firstParameter.isEmpty())

        val secondParameter = (testWithTwo.before.parameters.singleOrNull() as? TsTestValue.TsClass)?.properties
        check(secondParameter != null && secondParameter.size == 1) {
            "Expected 1 property in second parameter, got ${secondParameter?.size ?: 0}"
        }
        check(secondParameter.containsKey("x") == true) {
            "Expected property 'x' in second parameter"
        }
    }

    @Test
    fun runF3A() {
        /**
         *     // f3a: require number explicitly, still use '+' in the branch
         *     f3a(o: any) {
         *       if (typeof o.x === "number" && o.x + 1 > 0) return 1;
         *       return -1;
         *     }
         */
        val tests = generateTestsFor("f3a")

        check(tests.size == 3) { "Expected 3 tests for f3, got ${tests.size}" }

        val successBranch = tests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        val failedBranches = tests.filter { it !== successBranch }

        // Checks for success branch
        val succArg = successBranch.before.parameters.single()
        check(succArg is TsTestValue.TsClass) { "Expected TsObject for success branch, got ${succArg::class.simpleName}" }
        val succField = succArg.properties.entries.single()
        check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
        check(succField.value is TsTestValue.TsNumber || succField.value is TsTestValue.TsBoolean) {
            "Expected TsNumber or TsBoolean for 'x' in success branch, got ${succField.value::class.simpleName}"
        }
        val succValue = (succField.value as? TsTestValue.TsNumber)?.number
            ?: (succField.value as? TsTestValue.TsBoolean)?.value?.toDouble()
            ?: error("Expected number or boolean value for 'x' in success branch, got ${succField.value::class.simpleName}")
        check(succValue + 1.0 > 0.0) {
            "Expected 'x + 1 > 0' in success branch, got 'x + 1 = $succValue'"
        }

        failedBranches.forEach { failedBranch ->
            // Checks for failed branch
            val failArg = failedBranch.before.parameters.single()
            if (failArg !is TsTestValue.TsClass) {
                // primitive parameters are fine
                return
            }

            val failField = failArg.properties.entries.single()
            check(failField.key == "x") { "Expected field 'x' in failed branch, got '${failField.key}'" }
            check(failField.value !is TsTestValue.TsNumber) {
                "Expected non-number for 'x' in failed branch, got ${failField.value::class.simpleName}"
            }
        }
    }

    @Test
    fun runF3B() {
        /**
         *     // f3b: '+' coerces many values to number
         *     f3b(o: any) {
         *       const y = o.x + 1;
         *       if (typeof y === "number") return 1;
         *       return -1;
         *     }
         */
        val tests = generateTestsFor("f3b")
        check(tests.size == 2) { "Expected 2 tests for f3b, got ${tests.size}" }

        val successBranch = tests.single { it.returnValue is TsTestValue.TsNumber }
        val failedBranch = tests.single { it !== successBranch }

        check(successBranch.before.parameters.size == 1) {
            "Expected 1 parameter in success branch, got ${successBranch.before.parameters.size}"
        }
        val succArg = successBranch.before.parameters.single()
        check(succArg is TsTestValue.TsClass) {
            "Expected TsObject for success branch, got ${succArg::class.simpleName}"
        }
        val succFields = succArg.properties.entries

        // Empty is a fine case since `undefined + 1 is NaN`
        if (succFields.isNotEmpty()) {
            val succField = succFields.single()
            check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
            check(succField.value is TsTestValue.TsNumber || succField.value is TsTestValue.TsUndefined || succField.value is TsTestValue.TsNull || succField.value is TsTestValue.TsBoolean) {
                "Expected TsNumber, TsBoolean, TsUndefined or TsNull for 'x' in success branch, got ${succField.value::class.simpleName}"
            }
        }

        check(failedBranch.before.parameters.size == 1) {
            "Expected 1 parameter in failed branch, got ${failedBranch.before.parameters.size}"
        }
        val failArg = failedBranch.before.parameters.single()
        check(failArg !is TsTestValue.TsClass || !failArg.properties.contains("x")) {
            "Expected no 'x' field in failed branch"
        }
    }


    @Test
    fun runF4() {
        /**
         *     // f4: writes then deletes; checks absence
         *     f4(o: any) {
         *       o.x = 1;
         *       delete o.x;
         *       if ("x" in o) return -1; // unreachable
         *       return 1;
         *     }
         */
        val methodName = "f4"
        val method = buildEtsMethod(
            name = methodName,
            enclosingClass = scene.projectClasses.first(),
            parameters = listOf(
                "o" to EtsAnyType
            ),
            returnType = EtsNumberType,
        ) {
            // o := arg(0)
            val o = local("o")
            assign(o, param(0))

            // TODO unsupported
        }

        val machine = TsMachine(scene, options, tsOptions)
        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }

        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        check(tests.size > 1) { "Expected at least 1 test for f4, got ${tests.size}" }
        val successTests = tests.filter { it.returnValue !is TsTestValue.TsException }
        successTests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
    }

    @Test
    fun runF5() {
        /**
         *     // f5: discriminated union
         *     type A = { kind: "A"; a: number };
         *     type B = { kind: "B"; b: string };
         *     f5(o: A | B) {
         *       if (o.kind === "A" && o.a > 0) return 1;
         *       return -1;
         *     }
         */
        generateTestsFor("f5")
    }

    @Test
    fun runF6() {
        /**
         *     // f6: method presence only
         *     f6(o: any) {
         *       if (typeof o.m === "function") return 1;
         *       return -1;
         *     }
         */
        generateTestsFor("f6")
    }

    @Test
    fun runF7() {
        /**
         *     // f7: method return value is constrained
         *     f7(o: any) {
         *       if (o.m() === 42) return 1;
         *       return -1;
         *     }
         */
        generateTestsFor("f7")
    }

    @Test
    fun runF8() {
        /**
         *     // f8: rejects null/undefined via == null
         *     f8(o: any) {
         *       if (o.x == null) return -1;
         *       return 0;
         *     }
         */
        generateTestsFor("f8")
    }

    @Test
    fun runF9() {
        /**
         *     // f9: nested field requirement
         *     f9(o: any) {
         *       if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
         *       return -1;
         *     }
         */
        generateTestsFor("f9")
    }
}
