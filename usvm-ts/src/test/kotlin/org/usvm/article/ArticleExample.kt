package org.usvm.article

import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.neq
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
import org.junit.jupiter.api.Disabled
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
    val tsOptions = TsOptions(checkFieldPresents = true, enableVisualization = false)

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
        check(tests.size == 3) { "Expected 3 tests for f1, got ${tests.size}" }

        // Check that we have tests for both branches
        val exception =
            tests.singleOrNull { it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined }
        val returnOne = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        val returnTwo = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }

        check(exception != null && returnOne != null && returnTwo != null)

        val retOneArg = returnOne.before.parameters.single()
        check(retOneArg is TsTestValue.TsClass) { "Expected TsObject for returnOne, got ${retOneArg::class.simpleName}" }
        val property = retOneArg.properties.entries.single()
        check(property.key == "x") { "Expected property 'x' in returnOne, got '${property.key}'" }
        check(property.value is TsTestValue.TsNumber) { "Expected TsNumber for 'x' in returnOne, got ${property.value::class.simpleName}" }

        val retTwoArg = returnTwo.before.parameters.single()
        check(retTwoArg !is TsTestValue.TsClass || "x" !in retTwoArg.properties || retTwoArg.properties.entries.single().value !is TsTestValue.TsNumber)
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

        val testWithException =
            tests.singleOrNull { it.returnValue is TsTestValue.TsException && it.before.parameters.single() == TsTestValue.TsUndefined }
        val testWithOne = tests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        val testWithTwo = tests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }

        check(testWithException != null) { "Expected a test with exception for f2, got none" }

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
         *   f3a(o: any) {
         *     if (typeof o.x === "number" && o.x + 1 > 0) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f3a")

        check(tests.size == 3) { "Expected 3 tests for f3, got ${tests.size}" }

        val exceptionBranch =
            tests.singleOrNull { it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined }
        check(exceptionBranch != null) { "Expected a test with exception for f3a, got none" }
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
         *   f3b(o: any) {
         *     const y = o.x + 1;
         *     if (Number.isNaN(y)) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f3b")
        check(tests.size == 3) { "Expected 3 tests for f3b, got ${tests.size}" }

        val exceptionBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsException &&
                it.before.parameters.size == 1 &&
                it.before.parameters.single() is TsTestValue.TsUndefined
        }
        check(exceptionBranch != null) { "Expected an exception test for f3b with argument undefined, got none" }

        val numericBranches = tests.filter { it !== exceptionBranch }
        check(numericBranches.size == 2) { "Expected 2 non-exception tests, got ${numericBranches.size}" }

        val successBranch = numericBranches.singleOrNull {
            it.returnValue is TsTestValue.TsNumber &&
                it.returnValue.number == 1.0
        }
        check(successBranch != null) { "Expected a success test for f3b returning 1, got none" }

        val failedBranch = numericBranches.single { it !== successBranch }
        check(
            failedBranch.returnValue is TsTestValue.TsNumber &&
                failedBranch.returnValue.number == 2.0
        ) { "Expected a failure test for f3b returning 2, got ${failedBranch.returnValue}" }

        check(successBranch.before.parameters.size == 1) {
            "Expected 1 parameter in success branch, got ${successBranch.before.parameters.size}"
        }
        val succArg = successBranch.before.parameters.single()
        check(succArg is TsTestValue.TsClass) {
            "Expected object (TsClass) for success branch, got ${succArg::class.simpleName}"
        }

        val succFields = succArg.properties.entries
        if (succFields.isEmpty()) {
            // {} is valid: undefined + 1 → NaN
        } else {
            check(succFields.size == 1) { "Success PMO must have at most one field, got ${succFields.size}" }
            val (succKey, succVal) = succFields.single()
            check(succKey == "x") { "Expected field 'x' in success branch, got '$succKey'" }
            when (succVal) {
                is TsTestValue.TsUndefined -> {
                    // ok: undefined + 1 → NaN
                }

                is TsTestValue.TsNumber -> {
                    // require NaN specifically
                    check(succVal.number.isNaN()) { "Expected NaN for 'x' in success branch, got ${succVal.number}" }
                }

                else -> error("Success branch: 'x' must be undefined or NaN, got ${succVal::class.simpleName}")
            }
        }

        // Must be an object with 'x' present and NOT (undefined or NaN).
        check(failedBranch.before.parameters.size == 1) {
            "Expected 1 parameter in failed branch, got ${failedBranch.before.parameters.size}"
        }
        val failArg = failedBranch.before.parameters.single()
        check(failArg is TsTestValue.TsClass) {
            "Expected object (TsClass) for failed branch, got ${failArg::class.simpleName}"
        }
        val failProps = failArg.properties
        check(failProps.contains("x")) { "Failure PMO must have field 'x'" }
        val failX = failProps["x"] ?: error("Failure PMO: missing value for 'x'")

        when (failX) {
            is TsTestValue.TsUndefined -> error("Failure branch: 'x' must not be undefined")
            is TsTestValue.TsNumber -> {
                check(!failX.number.isNaN()) { "Failure branch: 'x' must not be NaN" }
                // numbers like 0, 1, -1 are fine (0+1=1; 1+1=2; -1+1=0) → not NaN ⇒ return 2
            }

            is TsTestValue.TsString, is TsTestValue.TsBoolean, is TsTestValue.TsNull -> {
                // also fine: "a"+1 → "a1" (string), true+1 → 2 (number), null+1 → 1 (number)
            }

            else -> error("Failure branch: unexpected type for 'x': ${failX::class.simpleName}")
        }

    }

    @Test
    fun runF4() {
        /**
         *     // f4: writes then deletes; checks absence
         *     f4(o: any) {
         *       o.x = 1;
         *       delete o.x;
         *       if (o.x !== undefined) return -1; // unreachable
         *       return 1;
         *     }
         */
        val tests = generateTestsFor("f4")

        println("Generated tests for method: f4")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        check(tests.size > 1) { "Expected at least 1 test for f4, got ${tests.size}" }
        val successTests = tests.filter { it.returnValue !is TsTestValue.TsException }
        successTests.single { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }

        check(tests.none { (it.returnValue as? TsTestValue.TsNumber)?.number == -1.0 })
    }

    @Test
    @Disabled("Wrong result")
    fun runF5() {
        /**
         *     // f5: discriminated union
         *     class A = { kind: "A"; a: number };
         *     class B = { kind: "B"; b: string };
         *     f5(o: A | B) {
         *       if (o.kind === "A" && o.a > 0) return 1;
         *       return 2;
         *     }
         */
        generateTestsFor("f5")
    }

    @Test
    fun runF6Strict() {
        /**
         *   f6_strict(o: any) {
         *     if (o.x === 0) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f6_strict")

        val exceptionBranch =
            tests.singleOrNull { it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined }
        check(exceptionBranch != null) { "Expected an exception test for f6_strict with argument undefined, got none" }
        val successBranch =
            tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        check(successBranch != null) { "Expected a success test for f6_strict returning 1, got none" }
        val failedBranch = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }
        check(failedBranch != null) { "Expected a failure test for f6_strict returning 2, got none" }

        val succArg = successBranch.before.parameters.singleOrNull()
        check(succArg is TsTestValue.TsClass) { "Expected TsObject for success branch, got ${succArg?.javaClass?.simpleName ?: "null"}" }
        val succField = succArg.properties.entries.singleOrNull()
        check(succField != null) { "Expected a single field in success branch, got none" }
        check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
        val value = succField.value
        check(value is TsTestValue.TsNumber && value.number == 0.0) {
            "Expected 'x' to be 0 in success branch, got ${value::class.simpleName} with value $value"
        }

        val failedBranchArg = failedBranch.before.parameters.singleOrNull()
        check(failedBranchArg !is TsTestValue.TsClass || "x" !in failedBranchArg.properties || failedBranchArg.properties.entries.single().value !is TsTestValue.TsNumber || (failedBranchArg.properties.entries.single().value as TsTestValue.TsNumber).number != 0.0) {
            "Expected failed branch to not have 'x' or have it as non-number, got ${failedBranchArg?.javaClass?.simpleName ?: "null"}"
        }
    }

    @Test
    fun runF7Loose() {
        /**
         *   f7_loose(o: any) {
         *     if (o.x == 0) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f7_loose")

        val exceptionBranch =
            tests.singleOrNull { it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined }
        check(exceptionBranch != null) { "Expected an exception test for f7_loose with argument undefined, got none" }

        val successBranch =
            tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0 }
        check(successBranch != null) { "Expected a success test for f7_loose returning 1, got none" }
        val failedBranch = tests.singleOrNull { it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0 }
        check(failedBranch != null) { "Expected a failure test for f7_loose returning 2, got none" }

        fun isLooselyZero(v: TsTestValue): Boolean = when (v) {
            is TsTestValue.TsNumber -> v.number == 0.0 || v.number == -0.0
            is TsTestValue.TsBoolean -> !v.value // false == 0
            else -> false // null/undefined/objects (as values) here shouldn't appear; treat as non-zero
        }

        val succArg = successBranch.before.parameters.singleOrNull()
        check(succArg is TsTestValue.TsClass) { "Expected TsObject for success branch, got ${succArg?.javaClass?.simpleName ?: "null"}" }
        val succField = succArg.properties.entries.singleOrNull()
        check(succField != null) { "Expected a single field in success branch, got none" }
        check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
        val succValue = succField.value
        check(isLooselyZero(succValue)) {
            "Expected 'x' to be loosely equal to 0 in success branch, got ${succValue::class.simpleName} with value $succValue"
        }

        val failedBranchArg = failedBranch.before.parameters.singleOrNull()
        check(
            failedBranchArg !is TsTestValue.TsClass ||
                !failedBranchArg.properties.contains("x") ||
                !isLooselyZero(requireNotNull(failedBranchArg.properties["x"]))
        ) {
            "Expected failed branch to not have 'x' loosely equal to 0, got ${failedBranchArg?.javaClass?.simpleName ?: "null"}"
        }
    }

    @Test
    fun runF8() {
        /**
         *   f8(o: any) {
         *     if (o.x == null) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f8")

        check(tests.size == 3) { "Expected 3 tests for f8, got ${tests.size}" }

        val exceptionBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined
        }
        val successBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0
        }
        val failedBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 2.0
        }

        check(exceptionBranch != null && successBranch != null && failedBranch != null) {
            "Expected one test with exception, one returning 1, and one returning 2, got: " +
                "exception=${exceptionBranch != null}, success=${successBranch != null}, failed=${failedBranch != null}"
        }

        val succArg = successBranch.before.parameters.singleOrNull()
        check(succArg is TsTestValue.TsClass) { "Expected TsObject for success branch, got ${succArg?.javaClass?.simpleName ?: "null"}" }
        val succField = succArg.properties.entries.singleOrNull()
        check(succField != null) { "Expected a single field in success branch, got none" }
        check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
        val succValue = succField.value
        check(succValue is TsTestValue.TsNull || succValue is TsTestValue.TsUndefined) {
            "Expected 'x' to be null or undefined in success branch, got ${succValue::class.simpleName} with value $succValue"
        }

        val failedArg = failedBranch.before.parameters.singleOrNull()
        check(
            failedArg !is TsTestValue.TsClass ||
                !failedArg.properties.contains("x") ||
                failedArg.properties["x"] !is TsTestValue.TsNull &&
                failedArg.properties["x"] !is TsTestValue.TsUndefined
        ) {
            "Expected failed branch to not have 'x' as null or undefined, got ${failedArg?.javaClass?.simpleName ?: "null"}"
        }
    }

    @Test
    @Disabled("Path constraints and fake type")
    fun runF9() {
        /**
         *   f9(o: any) {
         *     if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
         *     return 2;
         *   }
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

            val zero = local("%0")
            assign(
                zero,
                CustomValue {
                    EtsInstanceFieldRef(
                        EtsLocal("o", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "x", EtsUnknownType),
                        EtsUnknownType,
                    )
                }
            )

            val fstInExpr = CustomValue {
                EtsInExpr(EtsStringConstant("x"), EtsLocal("o", EtsUnknownType))
            }

            val fst = local("%1")
            assign(fst, and(fstInExpr, zero))

            val snd = local("%2")
            assign(
                snd,
                CustomValue {
                    EtsInstanceFieldRef(
                        EtsLocal("o", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "x", EtsUnknownType),
                        EtsUnknownType,
                    )
                }
            )

            val sndInExpr = CustomValue {
                EtsInExpr(
                    EtsStringConstant("y"),
                    EtsInstanceFieldRef(
                        EtsLocal("o", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "x", EtsUnknownType),
                        EtsUnknownType,
                    )
                )
            }

            val trd = local("%3")
            assign(trd, and(fst, sndInExpr))

            val fourth = local("%4")
            assign(
                fourth,
                CustomValue {
                    EtsInstanceFieldRef(
                        EtsLocal("o", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "x", EtsUnknownType),
                        EtsUnknownType,
                    )
                }
            )
            val fifth = local("%5")
            assign(
                fifth,
                CustomValue {
                    EtsInstanceFieldRef(
                        EtsLocal("%4", EtsUnknownType),
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, "y", EtsUnknownType),
                        EtsUnknownType,
                    )
                }
            )

            val sixth = local("%6")
            assign(sixth, eqq(fifth, const(true)))
            val seventh = local("%7")
            assign(seventh, and(trd, sixth))
            ifStmt(neq(seventh, const(false))) {
                ret(const(1.0))
            }.`else` {
                ret(const(2.0))
            }
        }


        val machine = TsMachine(scene, options, tsOptions)
        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }

        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests:\n" + formatTests(tests))

        // Basic checks for generated tests

        // TODO unsupported
    }

    @Test
    @Disabled("Incorrect IR")
    fun runF10() {
        /**
         *   f10_optchain(o: any) {
         *     if (o.x?.y === 1) return 1;
         *     return 2;
         *   }
         */
        generateTestsFor("f10_optchain")
    }

    @Test
    fun runF11NaN() {
        /**
         *   f11_nan(o: any) {
         *     if (typeof o.x === "number" && o.x !== o.x) return 1;
         *     return 2;
         *   }
         */
        val tests = generateTestsFor("f11_nan")
        val exceptionBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsException && it.before.parameters.single() is TsTestValue.TsUndefined
        }
        check(exceptionBranch != null) { "Expected an exception test for f11_nan with argument undefined, got none" }
        val succBranch = tests.singleOrNull {
            it.returnValue is TsTestValue.TsNumber && it.returnValue.number == 1.0
        }
        check(succBranch != null) { "Expected a success test for f11_nan returning 1, got none" }
        val failBranches = tests.filter { it !== succBranch && it !== exceptionBranch }
        check(failBranches.isNotEmpty()) { "Expected at least one failure test for f11_nan, got ${failBranches.size}" }

        val succArg = succBranch.before.parameters.singleOrNull()
        check(succArg is TsTestValue.TsClass) { "Expected TsObject for success branch, got ${succArg?.javaClass?.simpleName ?: "null"}" }
        val succField = succArg.properties.entries.singleOrNull()
        check(succField != null) { "Expected a single field in success branch, got none" }
        check(succField.key == "x") { "Expected field 'x' in success branch, got '${succField.key}'" }
        val succValue = succField.value
        check(succValue is TsTestValue.TsNumber && succValue.number.isNaN()) {
            "Expected 'x' to be NaN in success branch, got ${succValue::class.simpleName} with value $succValue"
        }

        val failedArgs = failBranches.map { (it.before.parameters.single() as TsTestValue.TsClass).properties }
        val fstCondition =
            failedArgs.singleOrNull { it.contains("x") && it["x"] is TsTestValue.TsNumber && !(it["x"] as TsTestValue.TsNumber).number.isNaN() } != null
        val sndCondition =
            failedArgs.singleOrNull { !it.contains("x") || it.contains("x") && it["x"] !is TsTestValue.TsNumber } != null

        if (failedArgs.size == 2) {
            check(fstCondition && sndCondition)
        } else {
            check(fstCondition || sndCondition)
        }
    }
}
