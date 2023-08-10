package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ClassForTestClinitSectionsTest : JavaMethodTestRunner() {
// TODO unsupported
    //    @Test
//    fun testClinitWithoutClinitAnalysis() {
//            withProcessingClinitSections(value = false) {
//                checkExecutionMatches(
//                    ClassForTestClinitSections::resultDependingOnStaticSection,
//                    eq(2),
//                    { _, r -> r == -1 },
//                    { _, r -> r == 1 }
//                )
//            }
//        }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1]") // todo: treat statics as input values
    fun testClinitWithClinitAnalysis() {
        checkDiscoveredProperties(
            ClassForTestClinitSections::resultDependingOnStaticSection,
            eq(2),
            { _, r -> r == -1 },
            { _, r -> r == 1 }
        )
    }

//    @Test
//    fun testProcessConcretelyWithoutClinitAnalysis() {
//        withoutConcrete {
//            withProcessingClinitSections(value = false) {
//                withProcessingAllClinitSectionsConcretely(value = true) {
//                    checkExecutionMatches(
//                        ClassForTestClinitSections::resultDependingOnStaticSection,
//                        eq(2),
//                        { _, r -> r == -1 },
//                        { _, r -> r == 1 }
//                    )
//                }
//            }
//        }
//    }
//
//    @Test
//    fun testProcessClinitConcretely() {
//        withoutConcrete {
//            withProcessingAllClinitSectionsConcretely(value = true) {
//                checkExecutionMatches(
//                    ClassForTestClinitSections::resultDependingOnStaticSection,
//                    eq(1),
//                    { _, r -> r == -1 },
//                    coverage = atLeast(71)
//                )
//            }
//        }
//    }
}

