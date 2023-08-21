package org.usvm.samples.enums

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.enums.ClassWithEnum.StatusEnum
import org.usvm.samples.enums.ClassWithEnum.StatusEnum.ERROR
import org.usvm.samples.enums.ClassWithEnum.StatusEnum.READY
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException


class ClassWithEnumTest : JavaMethodTestRunner() {
// TODO unsupported

    //    @Test
//    fun testOrdinal() {
//        withoutConcrete {
//            checkAllCombinations(ClassWithEnum::useOrdinal)
//        }
//    }

    @Test
    fun testGetter() {
        checkDiscoveredProperties(
            ClassWithEnum::useGetter,
            eq(2),
            { _, s, r -> s == null && r == -1 },
            { _, s, r -> s != null && r == 0 },
        )
    }

    @Test
    fun testDifficultIfBranch() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            ClassWithEnum::useEnumInDifficultIf,
            ignoreNumberOfAnalysisResults,
            { _, s, r -> s.equals("TRYIF", ignoreCase = true) && r == 1 },
            { _, s, r -> !s.equals("TRYIF", ignoreCase = true) && r == 2 },
        )
    }

    @Test // todo: Support enums
    fun testNullParameter() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            ClassWithEnum::nullEnumAsParameter,
            between(2..3),
            { _, e, _ -> e == null },
            { _, e, r -> e == READY && r == 0 || e == ERROR && r == -1 },
        )
    }

    @Test
    fun testNullField() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithEnum::nullField,
            eq(3),
            { _, e, r -> e == null && r.isException<NullPointerException>() },
            { _, e, r -> e == ERROR && r.isException<NullPointerException>() },
            { _, e, r -> e == READY && r.getOrNull()!! == 3 && READY.s.length == 3 },
        )
    }

    @Test
    fun testChangeEnum() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithEnum::changeEnum,
            eq(2),
            { _, e, r -> e == READY && r.getOrNull()!! == ERROR.ordinal },
            { _, e, r -> (e == ERROR || e == null) && r.getOrNull()!! == READY.ordinal },
        )
    }

    @Test
    fun testChangeMutableField() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithEnum::changeMutableField,
            eq(2),
            { _, e, r -> e == READY && r.getOrNull()!! == 2 },
            { _, e, r -> (e == null || e == ERROR) && r.getOrNull()!! == -2 },
        )
    }

    @Test
    fun testCheckName() = disableTest("Expected exactly 3 executions, but 7 found") {
        checkDiscoveredProperties(
            ClassWithEnum::checkName,
            eq(3),
            { _, s, _ -> s == null },
            { _, s, r -> s == READY.name && r == ERROR.name },
            { _, s, r -> s != READY.name && r == READY.name },
        )
    }

    // TODO unsupported matchers
    /*
        @Test
        fun testChangingStaticWithEnumInit() {
            checkThisAndStaticsAfter(
                ClassWithEnum::changingStaticWithEnumInit,
                eq(1),
                { _, t, staticsAfter, r ->
                    // We cannot check `x` since it is not a meaningful value since
                    // it is accessed only in a static initializer.

                    // For some reasons x is inaccessible
                    // val x = FieldId(t.javaClass.id, "x").jField.get(t) as Int

                    val y = staticsAfter[FieldId(ClassWithEnum.ClassWithStaticField::class.id, "y")]!!.value as Int

                    val areStaticsCorrect = */
    /*x == 1 &&*//*
 y == 11
                areStaticsCorrect && r == true
            }
        )
    }
*/

    @Test
    fun testVirtualFunction() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            ClassWithEnum::virtualFunction,
            eq(3),
            { _, parameter, _ -> parameter == null },
            { _, parameter, r -> r == 1 && parameter == ERROR },
            { _, parameter, r -> r == 0 && parameter == READY },
        )
    }

    // TODO kFunction0
//    @Test
//    fun testEnumValues() {
//        checkExecutionMatches(
//            StatusEnum::values,
//            eq(1),
//            { _, r -> r.contentEquals(arrayOf(READY, ERROR)) },
//        )
//    }

    @Test
    fun testFromCode() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            StatusEnum::fromCode,
            eq(3),
            { code, r -> code == 10 && r == READY },
            { code, r -> code == -10 && r == ERROR },
            { code, r -> code !in setOf(10, -10) && r == null }, // IllegalArgumentException
        )
    }

    @Test
    fun testFromIsReady() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            StatusEnum::fromIsReady,
            eq(2),
            { isFirst, r -> isFirst && r == READY },
            { isFirst, r -> !isFirst && r == ERROR },
        )
    }

    @Test
    fun testPublicGetCodeMethod() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkThisAndParamsMutations(
            StatusEnum::publicGetCode,
            between(1..2),
            { enumInstance, _, r -> enumInstance == READY && r == 10 || enumInstance == ERROR && r == -10 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testImplementingInterfaceEnumInDifficultBranch() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            ClassWithEnum::implementingInterfaceEnumInDifficultBranch,
            ignoreNumberOfAnalysisResults,
            { _, s, r -> s.equals("SUCCESS", ignoreCase = true) && r == 0 },
            { _, s, r -> !s.equals("SUCCESS", ignoreCase = true) && r == 2 },
        )
    }

    @Test
    fun testAffectSystemStaticAndUseInitEnumFromIt() {
        checkDiscoveredProperties(
            ClassWithEnum::affectSystemStaticAndInitEnumFromItAndReturnField,
            eq(1),
            { _, r -> r == true },
        )
    }

    @Test
    fun testAffectSystemStaticAndInitEnumFromItAndGetItFromEnumFun() {
        checkDiscoveredProperties(
            ClassWithEnum::affectSystemStaticAndInitEnumFromItAndGetItFromEnumFun,
            eq(1),
            { _, r -> r == true },
        )
    }
}