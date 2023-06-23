package org.usvm.samples.collections


import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

@Disabled("Unsupported")
class OptionalsTest : JavaMethodTestRunner() {
    @Test
    fun testCreate() {
        checkDiscoveredPropertiesWithExceptions(
            Optionals::create,
            eq(2),
            { _, value, result -> value == null && result.isException<NullPointerException>() },
            { _, value, result -> value != null && result.getOrNull()!!.get() == value },
        )
    }

    @Test
    fun testCreateInt() {
        checkDiscoveredProperties(
            Optionals::createInt,
            eq(1),
            { _, value, result -> result != null && result.asInt == value },
        )
    }

    @Test
    fun testCreateLong() {
        checkDiscoveredProperties(
            Optionals::createLong,
            eq(1),
            { _, value, result -> result != null && result.asLong == value },
        )
    }

    @Test
    fun testCreateDouble() {
        checkDiscoveredProperties(
            Optionals::createDouble,
            eq(1),
            { _, value, result -> result != null && result.asDouble == value || result!!.asDouble.isNaN() },
        )
    }

    // TODO unsupported matchers
    
//    @Test
//    fun testCreateNullable() {
//        checkStatics(
//            Optionals::createNullable,
//            eq(2),
//            { _, value, _, result -> value == null && result === Optional.empty<Int>() },
//            { _, value, _, result -> value != null && result.get() == value },
//        )
//    }
//
//    @Test
//    fun testCreateEmpty() {
//        checkStatics(
//            Optionals::createEmpty,
//            eq(1),
//            { _, result -> result === Optional.empty<Int>() },
//        )
//    }
//
//    @Test
//    fun testCreateIntEmpty() {
//        checkStatics(
//            Optionals::createIntEmpty,
//            eq(1),
//            { _, result -> result === OptionalInt.empty() },
//        )
//    }
//
//    @Test
//    fun testCreateLongEmpty() {
//        checkStatics(
//            Optionals::createLongEmpty,
//            eq(1),
//            { _, result -> result === OptionalLong.empty() },
//        )
//    }
//
//    @Test
//    fun testCreateDoubleEmpty() {
//        checkStatics(
//            Optionals::createDoubleEmpty,
//            eq(1),
//            { _, result -> result === OptionalDouble.empty() },
//        )
//    }
//
//    @Test
//    fun testGetValue() {
//        checkStatics(
//            Optionals::getValue,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional != null && optional === Optional.empty<Int>() && result == null },
//            { optional, _, result -> optional != null && result == optional.get() },
//        )
//    }
//
//    @Test
//    fun testGetIntValue() {
//        checkStatics(
//            Optionals::getIntValue,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional != null && optional === OptionalInt.empty() && result == null },
//            { optional, _, result -> optional != null && result == optional.asInt },
//        )
//    }
//
//    @Test
//    fun testGetLongValue() {
//        checkStatics(
//            Optionals::getLongValue,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional != null && optional === OptionalLong.empty() && result == null },
//            { optional, _, result -> optional != null && result == optional.asLong },
//        )
//    }
//
//    @Test
//    fun testGetDoubleValue() {
//        checkStatics(
//            Optionals::getDoubleValue,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional != null && optional === OptionalDouble.empty() && result == null },
//            { optional, _, result -> optional != null && result == optional.asDouble },
//        )
//    }
//
//    @Test
//    fun testGetWithIsPresent() {
//        checkStatics(
//            Optionals::getWithIsPresent,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result == null },
//            { optional, _, result -> optional.get() == result },
//        )
//    }
//
//    @Test
//    fun testCountIfPresent() {
//        checkStatics(
//            Optionals::countIfPresent,
//            eq(3),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result == 0 },
//            { optional, _, result -> optional.get() == result },
//        )
//    }
//
//    @Test
//    fun testCountIntIfPresent() {
//        checkStatics(
//            Optionals::countIntIfPresent,
//            ignoreNumberOfAnalysisResults,
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === OptionalInt.empty() && result == 0 },
//            { optional, _, result -> optional.asInt == result },
//        )
//    }
//
//    @Test
//    fun testCountLongIfPresent() {
//        checkStatics(
//            Optionals::countLongIfPresent,
//            ignoreNumberOfAnalysisResults,
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === OptionalLong.empty() && result == 0L },
//            { optional, _, result -> optional.asLong == result },
//        )
//    }
//
//    @Test
//    fun testCountDoubleIfPresent() {
//        checkStatics(
//            Optionals::countDoubleIfPresent,
//            ignoreNumberOfAnalysisResults,
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === OptionalDouble.empty() && result == 0.0 },
//            { optional, _, result -> optional.asDouble == result },
//        )
//    }
//
//    @Test
//    fun testFilterLessThanZero() {
//        checkStatics(
//            Optionals::filterLessThanZero,
//            eq(4),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result === optional },
//            { optional, _, result -> optional.get() >= 0 && result == optional },
//            { optional, _, result -> optional.get() < 0 && result === Optional.empty<Int>() },
//        )
//    }
//
//    @Test
//    fun testAbsNotNull() {
//        checkStatics(
//            Optionals::absNotNull,
//            eq(4),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result === optional },
//            { optional, _, result -> optional.get() < 0 && result.get() == -optional.get() },
//            { optional, _, result -> optional.get() >= 0 && result == optional },
//        )
//    }
//
//    @Test
//    fun testMapLessThanZeroToNull() {
//        checkStatics(
//            Optionals::mapLessThanZeroToNull,
//            eq(4),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result === optional },
//            { optional, _, result -> optional.get() < 0 && result === Optional.empty<Int>() },
//            { optional, _, result -> optional.get() >= 0 && result == optional },
//        )
//    }
//
//    @Test
//    fun testFlatAbsNotNull() {
//        checkStatics(
//            Optionals::flatAbsNotNull,
//            eq(4),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result === optional },
//            { optional, _, result -> optional.get() < 0 && result.get() == -optional.get() },
//            { optional, _, result -> optional.get() >= 0 && result == optional },
//        )
//    }
//
//    @Test
//    fun testFlatMapWithNull() {
//        checkStatics(
//            Optionals::flatMapWithNull,
//            eq(5),
//            { optional, _, _ -> optional == null },
//            { optional, _, result -> optional === Optional.empty<Int>() && result === optional },
//            { optional, _, result -> optional.get() < 0 && result === Optional.empty<Int>() },
//            { optional, _, result -> optional.get() > 0 && result == optional },
//            { optional, _, result -> optional.get() == 0 && result == null },
//        )
//    }
//
//    @Test
//    fun testLeftOrElseRight() {
//        checkStatics(
//            Optionals::leftOrElseRight,
//            eq(3),
//            { _, left, _, _, _ -> left == null },
//            { _, left, right, _, result -> left === Optional.empty<Int>() && result == right },
//            { _, left, _, _, result -> left.isPresent && result == left.get() },
//        )
//    }
//
//    @Test
//    fun testLeftIntOrElseRight() {
//        checkStatics(
//            Optionals::leftIntOrElseRight,
//            eq(3),
//            { _, left, _, _, _ -> left == null },
//            { _, left, right, _, result -> left === OptionalInt.empty() && result == right },
//            { _, left, _, _, result -> left.isPresent && result == left.asInt },
//        )
//    }
//
//
//    @Test
//    fun testLeftLongOrElseRight() {
//        checkStatics(
//            Optionals::leftLongOrElseRight,
//            eq(3),
//            { _, left, _, _, _ -> left == null },
//            { _, left, right, _, result -> left === OptionalLong.empty() && result == right },
//            { _, left, _, _, result -> left.isPresent && result == left.asLong },
//        )
//    }
//
//
//    @Test
//    fun testLeftDoubleOrElseRight() {
//        checkStatics(
//            Optionals::leftDoubleOrElseRight,
//            eq(3),
//            { _, left, _, _, _ -> left == null },
//            { _, left, right, _, result -> left === OptionalDouble.empty() && (result == right || result.isNaN() && right.isNaN()) },
//            { _, left, _, _, result -> left.isPresent && (result == left.asDouble || result.isNaN() && left.asDouble.isNaN()) },
//        )
//    }
//
//
//    @Test
//    fun testLeftOrElseGetOne() {
//        checkStatics(
//            Optionals::leftOrElseGetOne,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === Optional.empty<Int>() && result == 1 },
//            { _, left, _, result -> left.isPresent && result == left.get() },
//        )
//    }
//
//    @Test
//    fun testLeftIntOrElseGetOne() {
//        checkStatics(
//            Optionals::leftIntOrElseGetOne,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalInt.empty() && result == 1 },
//            { _, left, _, result -> left.isPresent && result == left.asInt },
//        )
//    }
//
//    @Test
//    fun testLeftLongOrElseGetOne() {
//        checkStatics(
//            Optionals::leftLongOrElseGetOne,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalLong.empty() && result == 1L },
//            { _, left, _, result -> left.isPresent && result == left.asLong },
//        )
//    }
//
//    @Test
//    fun testLeftDoubleOrElseGetOne() {
//        checkStatics(
//            Optionals::leftDoubleOrElseGetOne,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalDouble.empty() && result == 1.0 },
//            { _, left, _, result -> left.isPresent && result == left.asDouble },
//        )
//    }
//
//    @Test
//    fun testLeftOrElseThrow() {
//        checkStatics(
//            Optionals::leftOrElseThrow,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === Optional.empty<Int>() && result == null },
//            { _, left, _, result -> left.isPresent && result == left.get() },
//        )
//    }
//
//    @Test
//    fun testLeftIntOrElseThrow() {
//        checkStatics(
//            Optionals::leftIntOrElseThrow,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalInt.empty() && result == null },
//            { _, left, _, result -> left.isPresent && result == left.asInt },
//        )
//    }
//
//    @Test
//    fun testLeftLongOrElseThrow() {
//        checkStatics(
//            Optionals::leftLongOrElseThrow,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalLong.empty() && result == null },
//            { _, left, _, result -> left.isPresent && result == left.asLong },
//        )
//    }
//
//    @Test
//    fun testLeftDoubleOrElseThrow() {
//        checkStatics(
//            Optionals::leftDoubleOrElseThrow,
//            eq(3),
//            { _, left, _, _ -> left == null },
//            { _, left, _, result -> left === OptionalDouble.empty() && result == null },
//            { _, left, _, result -> left.isPresent && result == left.asDouble },
//        )
//    }

    @Test
    fun testEqualOptionals() {
        checkDiscoveredProperties(
            Optionals::equalOptionals,
            between(4..7),
            { _, left, _, _ -> left == null },
            { _, left, right, result -> left != null && left != right && result != null && !result },
            { _, left, right, result -> left != null && left === right && !left.isPresent && !right.isPresent && result != null && result },
            { _, left, right, result -> left != null && left == right && left.isPresent && right.isPresent && result != null && result },
        )
    }

    @Test
    fun testEqualOptionalsInt() {
        checkDiscoveredProperties(
            Optionals::equalOptionalsInt,
            between(4..8),
            { _, left, _, _ -> left == null },
            { _, left, right, result -> left != null && left != right && result != null && !result },
            { _, left, right, result -> left != null && left === right && !left.isPresent && !right.isPresent && result != null && result },
            { _, left, right, result -> left != null && left == right && left.isPresent && right.isPresent && result != null && result },
        )
    }

    @Test
    fun testEqualOptionalsLong() {
        checkDiscoveredProperties(
            Optionals::equalOptionalsLong,
            between(4..8),
            { _, left, _, _ -> left == null },
            { _, left, right, result -> left != null && left != right && result != null && !result },
            { _, left, right, result -> left != null && left === right && !left.isPresent && !right.isPresent && result != null && result },
            { _, left, right, result -> left != null && left == right && left.isPresent && right.isPresent && result != null && result },
        )
    }

    @Test
    fun testEqualOptionalsDouble() {
        checkDiscoveredProperties(
            Optionals::equalOptionalsDouble,
            between(4..8),
            { _, left, _, _ -> left == null  },
            { _, left, right, result -> left != null && left != right && result != null && !result },
            { _, left, right, result -> left != null && left === right && !left.isPresent && !right.isPresent && result != null && result },
            { _, left, right, result -> left != null && left == right && left.isPresent && right.isPresent && result != null && result },
        )
    }

    @Test
    fun testOptionalOfPositive() {
        checkDiscoveredProperties(
            Optionals::optionalOfPositive,
            eq(2),
            { _, value, result -> value > 0 && result != null && result.isPresent && result.get() == value },
            { _, value, result -> value <= 0 && result != null && !result.isPresent }
        )
    }
}