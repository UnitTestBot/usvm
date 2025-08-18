package org.usvm.article

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
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
    val tsOptions = TsOptions(checkFieldPresents = true)

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
         *       throw new Error("bad");
         *     }
         *
         */
        generateTestsFor("f1")
    }

    @Test
    fun runF2() {
        /**
         *     // f2: throws if "x" is missing
         *     f2(o: any) {
         *       if (!("x" in o)) throw new Error("miss");
         *       return 0;
         *     }
         */
        generateTestsFor("f2")
    }

    @Test
    fun runF3A() {
        /**
         *     // f3a: require number explicitly, still use '+' in the branch
         *     f3a(o: any) {
         *       if (typeof o.x === "number" && o.x + 1 > 0) return 1;
         *       throw new Error("not-number-branch");
         *     }
         */
        val tests = generateTestsFor("f3a")

        check(tests.size == 3) { "Expected 3 tests for f3, got ${tests.size}" }

        val successBranch = tests.single { it.returnValue is TsTestValue.TsNumber }
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
         *       throw new Error("string-branch");
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
         *       o.x = 1;            // создаётся *внутри*, не часть входа
         *       delete o.x;
         *       if ("x" in o) throw new Error("still here");
         *       return 0;
         *     }
         */
        val tests = generateTestsFor("f4")

        check(tests.size == 1) { "Expected 1 test for f4, got ${tests.size}" }
        val test = tests.single()
        check(test.returnValue is TsTestValue.TsNumber) {
            "Expected TsNumber return value for f4, got ${test.returnValue::class.simpleName}"
        }
        check(test.returnValue.number == 0.0) {
            "Expected return value 0 for f4, got ${test.returnValue.number}"
        }


    }

    @Test
    fun runF5() {
        /**
         *     // f5: destructuring with default
         *     f5({ x = 1 }: { x?: number }) {
         *       if (x > 0) return 1;
         *       throw new Error("non-positive");
         *     }
         */
        generateTestsFor("f5")
    }

    @Test
    fun runF6() {
        /**
         *     // f6: discriminated union
         *     type A = { kind: "A"; a: number };
         *     type B = { kind: "B"; b: string };
         *     f6(o: A | B) {
         *       if (o.kind === "A" && o.a > 0) return 1;
         *       throw new Error("not A>0");
         *     }
         */
        generateTestsFor("f6")
    }

    @Test
    fun runF7() {
        /**
         *     // f7: method presence only
         *     f7(o: any) {
         *       if (typeof o.m === "function") return 1;
         *       throw new Error("no method");
         *     }
         */
        generateTestsFor("f7")
    }

    @Test
    fun runF8() {
        /**
         *     // f8: method return value is constrained
         *     f8(o: any) {
         *       if (o.m() === 42) return 1;
         *       throw new Error("bad ret");
         *     }
         */
        generateTestsFor("f8")
    }

    @Test
    fun runF9() {
        /**
         *     // f9: rejects null/undefined via == null
         *     f9(o: any) {
         *       if (o.x == null) throw new Error("nullish");
         *       return 0;
         *     }
         */
        generateTestsFor("f9")
    }

    @Test
    fun runF10() {
        /**
         *     // f10: nested field requirement
         *     f10(o: any) {
         *       if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
         *       throw new Error("missing nested");
         *     }
         */
        generateTestsFor("f10")
    }
}
