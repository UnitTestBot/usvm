package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


// TODO failed Kotlin compilation SAT-1332
class SetIteratorsTest : JavaMethodTestRunner() {
    @Test
    fun testReturnIterator() {
        checkExecutionMatches(
            SetIterators::returnIterator,
            ignoreNumberOfAnalysisResults,
            { _, s, r -> s.isEmpty() && r!!.asSequence().toSet().isEmpty() },
            { _, s, r -> s.isNotEmpty() && r!!.asSequence().toSet() == s },
        )
    }

    @Test
    fun testIteratorHasNext() {
        checkExecutionMatches(
            SetIterators::iteratorHasNext,
            between(3..4),
            { _, set, _ -> set == null },
            { _, set, result -> set.isEmpty() && result == 0 },
            { _, set, result -> set.isNotEmpty() && result == set.size },
        )
    }

    @Test
    fun testIteratorNext() {
        checkWithExceptionExecutionMatches(
            SetIterators::iteratorNext,
            between(3..4),
            { _, set, result -> set == null && result.isException<NullPointerException>() },
            { _, set, result -> set != null && set.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for set is LinkedHashSet
            { _, set, result -> set != null && set.isNotEmpty() && result.getOrNull() == set.first() },
        )
    }

    @Test
    fun testIteratorRemove() {
        checkWithExceptionExecutionMatches(
            SetIterators::iteratorRemove,
            between(3..4),
            { _, set, result -> set == null && result.isException<NullPointerException>() },
            { _, set, result -> set.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for set is LinkedHashSet
            { _, set, result ->
                val firstElement = set.first()
                val resultSet = result.getOrNull()!!
                val resultDoesntContainFirstElement = resultSet.size == set.size - 1 && firstElement !in resultSet
                set.isNotEmpty() && set.containsAll(resultSet) && resultDoesntContainFirstElement
            },
        )
    }

    @Test
    fun testIteratorRemoveOnIndex() {
        checkWithExceptionExecutionMatches(
            SetIterators::iteratorRemoveOnIndex,
            ge(5),
            { _, _, i, result -> i == 0 && result.isSuccess && result.getOrNull() == null },
            { _, set, _, result -> set == null && result.isException<NullPointerException>() },
            { _, set, i, result -> set != null && i < 0 && result.isException<IllegalStateException>() },
            { _, set, i, result -> i > set.size && result.isException<NoSuchElementException>() },
            // test should work as long as default class for set is LinkedHashSet
            { _, set, i, result ->
                val ithElement = set.toList()[i - 1]
                val resultSet = result.getOrNull()!!
                val iInIndexRange = i in 0..set.size
                val resultDoesntContainIthElement = resultSet.size == set.size - 1 && ithElement !in resultSet
                iInIndexRange && set.containsAll(resultSet) && resultDoesntContainIthElement
            },
        )
    }

    @Test
    fun testIterateForEach() {
        checkExecutionMatches(
            SetIterators::iterateForEach,
            ignoreNumberOfAnalysisResults,
            { _, set, _ -> set == null },
            { _, set, _ -> set != null && null in set },
            { _, set, result -> set != null && result == set.sum() },
        )
    }


    @Test
    fun testIterateWithIterator() {
        checkExecutionMatches(
            SetIterators::iterateWithIterator,
            ignoreNumberOfAnalysisResults,
            { _, set, _ -> set == null },
            { _, set, _ -> set != null && null in set },
            { _, set, result -> set != null && result == set.sum() },
        )
    }
}