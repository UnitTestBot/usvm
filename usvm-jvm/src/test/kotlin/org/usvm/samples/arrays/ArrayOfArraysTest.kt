package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.casts.ColoredPoint
import org.usvm.samples.casts.Point
import org.usvm.test.util.checkers.eq


@Suppress("NestedLambdaShadowedImplicitParameter")
@Disabled("Unsupported")
internal class ArrayOfArraysTest : JavaMethodTestRunner() {
    @Test
    fun testDefaultValues() {
        checkDiscoveredProperties(
            ArrayOfArrays::defaultValues,
            eq(1),
            { _, r -> r != null && r.single() == null },
        )
    }

    @Test
    fun testExample() {
        checkDiscoveredProperties(
            ArrayOfArrays::sizesWithoutTouchingTheElements,
            eq(1),
            { _, r -> r != null && r.size == 10 && r.all { it.size == 3 && it.all { it == 0 } } },
        )
    }

    @Test
    @Disabled("Impossible NPE found")
    fun testDefaultValuesWithoutLastDimension() {
        checkDiscoveredProperties(
            ArrayOfArrays::defaultValuesWithoutLastDimension,
            eq(1),
            { _, r -> r != null && r.all { it.size == 4 && it.all { it.size == 4 && it.all { it == null } } } },
        )
    }

    @Test
    fun testCreateNewMultiDimensionalArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::createNewMultiDimensionalArray,
            eq(4),
            { _, i, j, _ -> i < 0 || j < 0 },
            { _, i, j, r -> i == 0 && j >= 0 && r != null && r.size == 2 && r.all { it.isEmpty() } },
            { _, i, j, r ->
                val indicesConstraint = i > 0 && j == 0
                val arrayPropertiesConstraint = r != null && r.size == 2
                val arrayContentConstraint = r?.all { it.size == i && it.all { it.isEmpty() } } ?: false

                indicesConstraint && arrayPropertiesConstraint && arrayContentConstraint
            },
            { _, i, j, r ->
                val indicesConstraint = i > 0 && j > 0
                val arrayPropertiesConstraint = r != null && r.size == 2
                val arrayContentConstraint =
                    r?.all {
                        it.size == i && it.all {
                            it.size == j && it.all {
                                it.size == 3 && it.all { it == 0 }
                            }
                        }
                    }

                indicesConstraint && arrayPropertiesConstraint && (arrayContentConstraint ?: false)
            }
        )
    }

    @Test
    fun testDefaultValuesWithoutTwoDimensions() {
        checkDiscoveredProperties(
            ArrayOfArrays::defaultValuesWithoutTwoDimensions,
            eq(2),
            { _, i, r -> i < 2 && r == null },
            { _, i, r -> i >= 2 && r != null && r.all { it.size == i && it.all { it == null } } },
        )
    }

    @Test
    fun testDefaultValuesNewMultiArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::defaultValuesNewMultiArray,
            eq(1),
            { _, r -> r != null && r.single().single().single() == 0 },
        )
    }

    @Test
    fun testSimpleExample() {
        checkDiscoveredProperties(
            ArrayOfArrays::simpleExample,
            eq(7),
            { _, m, r -> m.size >= 3 && m[1] === m[2] && r == null },
            { _, m, r -> m.size >= 3 && m[1] !== m[2] && m[0] === m[2] && r == null },
            { _, m, _ -> m.size >= 3 && m[1].size < 2 },
            { _, m, _ -> m.size >= 3 && m[1][1] == 1 && m[2].size < 3 },
            { _, m, r -> m.size >= 3 && m[1][1] == 1 && m[2].size >= 3 && r != null && r[2][2] == 2 },
            { _, m, _ -> m.size >= 3 && m[1][1] != 1 && m[2].size < 3 },
            { _, m, r -> m.size >= 3 && m[1][1] != 1 && m[2].size >= 3 && r != null && r[2][2] == -2 },
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testSimpleExampleMutation() {
//        checkParamsMutations(
//            ArrayOfArrays::simpleExample,
//            eq(7),
//            { matrixBefore, matrixAfter -> matrixBefore[1][1] == 1 && matrixAfter[2][2] == 2 },
//            { matrixBefore, matrixAfter -> matrixBefore[1][1] != 1 && matrixAfter[2][2] == -2 },
//            coverage = DoNotCalculate
//        )
//    }

    @Test
    fun testIsIdentityMatrix() {
        checkDiscoveredProperties(
            ArrayOfArrays::isIdentityMatrix,
            eq(9),
            { _, m, _ -> m == null },
            { _, m, _ -> m.size < 3 },
            { _, m, _ -> m.size >= 3 && m.any { it == null } },
            { _, m, r -> m.size >= 3 && m.any { it.size != m.size } && r != null && !r },
            { _, m, r -> m.size >= 3 && m.size == m[0].size && m[0][0] != 1 && r != null && !r },
            { _, m, r -> m.size >= 3 && m.size == m[0].size && m[0][0] == 1 && m[0][1] != 0 && r != null && !r },
            { _, m, r -> m.size >= 3 && m.size == m[0].size && m[0][0] == 1 && m[0][1] == 0 && m[0][2] != 0 && r != null && !r },
            { _, m, r ->
                val sizeConstraints = m.size >= 3 && m.size == m[0].size
                val valueConstraint = m[0][0] == 1 && m[0].drop(1).all { it == 0 }
                val resultCondition = m[1]?.size != m.size && r != null && !r

                sizeConstraints && valueConstraint && resultCondition
            },
            { _, m, r ->
                val sizeConstraint = m.size >= 3 && m.size == m.first().size
                val contentConstraint =
                    m.indices.all { i ->
                        m.indices.all { j ->
                            (i == j && m[i][j] == 1) || (i != j && m[i][j] == 0)
                        }
                    }

                sizeConstraint && contentConstraint && r != null && r
            },
        )
    }

    @Test
    fun testCreateNewThreeDimensionalArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::createNewThreeDimensionalArray,
            eq(2),
            { _, length, _, r -> length != 2 && r != null && r.isEmpty() },
            { _, length, constValue, r ->
                val sizeConstraint = length == 2 && r != null && r.size == length
                val contentConstraint =
                    r != null && r.all {
                        it.size == length && it.all {
                            it.size == length && it.all { it == constValue + 1 }
                        }
                    }

                sizeConstraint && contentConstraint
            }
        )
    }

    @Test
    fun testReallyMultiDimensionalArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::reallyMultiDimensionalArray,
            eq(8),
            { _, a, _ -> a == null },
            { _, a, _ -> a.size < 2 },
            { _, a, _ -> a.size >= 2 && a[1] == null },
            { _, a, _ -> a.size >= 2 && a[1].size < 3 },
            { _, a, _ -> a.size >= 2 && a[1].size >= 3 && a[1][2] == null },
            { _, a, _ -> a.size >= 2 && a[1].size >= 3 && a[1][2].size < 4 },
            { _, a, r ->
                val sizeConstraint = a.size >= 2 && a[1].size >= 3 && a[1][2].size >= 4
                val valueConstraint = a[1][2][3] == 12345 && r != null && r[1][2][3] == -12345

                sizeConstraint && valueConstraint
            },
            { _, a, r ->
                val sizeConstraint = a.size >= 2 && a[1].size >= 3 && a[1][2].size >= 4
                val valueConstraint = a[1][2][3] != 12345 && r != null && r[1][2][3] == 12345

                sizeConstraint && valueConstraint
            },
        )
    }

//    TODO unsupported matchers
//    @Test
//    fun testReallyMultiDimensionalArrayMutation() {
//        checkParamsMutations(
//            ArrayOfArrays::reallyMultiDimensionalArray,
//            ignoreNumberOfAnalysisResults,
//            { arrayBefore, arrayAfter -> arrayBefore[1][2][3] != 12345 && arrayAfter[1][2][3] == 12345 },
//            { arrayBefore, arrayAfter -> arrayBefore[1][2][3] == 12345 && arrayAfter[1][2][3] == -12345 },
//        )
//    }

    @Test
    fun testMultiDimensionalObjectsArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::multiDimensionalObjectsArray,
            eq(4),
            { _, a, _ -> a == null },
            { _, a, _ -> a.isEmpty() },
            { _, a, _ -> a.size == 1 },
            { _, a, r ->
                require(r != null && r[0] != null && r[1] != null)

                val propertiesConstraint = a.size > 1
                val zeroElementConstraints = r[0] is Array<*> && r[0].isArrayOf<ColoredPoint>() && r[0].size == 2
                val firstElementConstraints = r[1] is Array<*> && r[1].isArrayOf<Point>() && r[1].size == 1

                propertiesConstraint && zeroElementConstraints && firstElementConstraints
            },
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testMultiDimensionalObjectsArrayMutation() {
//        checkParamsMutations(
//            ArrayOfArrays::multiDimensionalObjectsArray,
//            ignoreNumberOfAnalysisResults,
//            { _, arrayAfter ->
//                arrayAfter[0] is Array<*> && arrayAfter[0].isArrayOf<ColoredPoint>() && arrayAfter[0].size == 2
//            },
//            { _, arrayAfter ->
//                arrayAfter[1] is Array<*> && arrayAfter[1].isArrayOf<Point>() && arrayAfter[1].size == 1
//            },
//        )
//    }

    @Test
    fun testFillMultiArrayWithArray() {
        checkDiscoveredProperties(
            ArrayOfArrays::fillMultiArrayWithArray,
            eq(3),
            { _, v, _ -> v == null },
            { _, v, r -> v.size < 2 && r != null && r.isEmpty() },
            { _, v, r ->
                val sizeConstraint = v.size >= 2
                val nullability = r != null
                val resultsPredicate = r != null && r.all { a ->
                    val arrayAsList = a.toList()
                    val mapIndexed = v.mapIndexed { i, elem -> elem + i }
                    arrayAsList == mapIndexed
                }

                sizeConstraint && nullability && resultsPredicate
            }
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testFillMultiArrayWithArrayMutation() {
//        checkParamsMutations(
//            ArrayOfArrays::fillMultiArrayWithArray,
//            ignoreNumberOfAnalysisResults,
//            { valueBefore, valueAfter -> valueAfter.withIndex().all { it.value == valueBefore[it.index] + it.index } }
//        )
//    }

    @Test
    fun testArrayWithItselfAnAsElement() {
        checkDiscoveredProperties(
            ArrayOfArrays::arrayWithItselfAnAsElement,
            eq(2),
            { _, a, r -> a !== a[0] && r == null },
            { _, a, r -> a === a[0] && a.contentDeepEquals(r) },
        )
    }
}