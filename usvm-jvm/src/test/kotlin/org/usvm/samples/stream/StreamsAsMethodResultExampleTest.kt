package org.usvm.samples.stream

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

class StreamsAsMethodResultExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamExample() {
        checkDiscoveredProperties(
            StreamsAsMethodResultExample::returningStreamExample,
            eq(2),
            { _, c, r -> c.isEmpty() && c == r!!.asList() },
            { _, c, r -> c.isNotEmpty() && c == r!!.asList() },
        )
    }

    // TODO unsupported
//    @Test
//    fun testReturningIntStreamExample() {
//        checkWithExceptionExecutionMatches(
//            StreamsAsMethodResultExample::returningIntStreamExample,
//            eq(3),
//            { _, c, r -> c.isEmpty() && c == r.getOrThrow().toList() },
//            { _, c, r -> c.isNotEmpty() && c.none { it == null } && c.toIntArray().contentEquals(r.getOrThrow().toArray()) },
//            { _, c, r -> c.isNotEmpty() && c.any { it == null } && r.isException<UtStreamConsumingException>() }
//        )
//    }

    // TODO unsupported
//    @Test
//    fun testReturningLongStreamExample() {
//        checkWithExceptionExecutionMatches(
//            StreamsAsMethodResultExample::returningLongStreamExample,
//            eq(3),
//            { _, c, r -> c.isEmpty() && c == r.getOrThrow().toList() },
//            { _, c, r -> c.isNotEmpty() && c.none { it == null } && c.map { it.toLong() }.toLongArray().contentEquals(r.getOrThrow().toArray()) },
//            { _, c, r -> c.isNotEmpty() && c.any { it == null } && r.isException<UtStreamConsumingException>() }
//        )
//    }
//
//    @Test
//    fun testReturningDoubleStreamExample() {
//        checkWithExceptionExecutionMatches(
//            StreamsAsMethodResultExample::returningDoubleStreamExample,
//            eq(3),
//            { _, c, r -> c.isEmpty() && c == r.getOrThrow().toList() },
//            { _, c, r -> c.isNotEmpty() && c.none { it == null } && c.map { it.toDouble() }.toDoubleArray().contentEquals(r.getOrThrow().toArray()) },
//            { _, c, r -> c.isNotEmpty() && c.any { it == null } && r.isException<UtStreamConsumingException>() }
//        )
//    }
}
