package org.usvm.samples.collections


import org.junit.jupiter.api.Disabled
import org.usvm.samples.JavaMethodTestRunner


internal class CustomerExamplesTest : JavaMethodTestRunner() {
// TODO unsupported matchers

//    @Test
//    fun testSimpleExample() {
//        checkStatics(
//            CustomerExamples::simpleExample,
//            eq(2),
//            { key, statics, r ->
//                val hashMap = statics.extractSingleStaticMap()
//                key !in hashMap && r == 2
//            },
//            { key, statics, r ->
//                val hashMap = statics.extractSingleStaticMap()
//                key in hashMap && r == 1
//            },
//        )
//    }
//
//    @Test
//    fun testStaticMap() {
//        checkStaticsWithThis(
//            CustomerExamples::staticMap,
//            ignoreNumberOfAnalysisResults,
//            { _, a, _, _, _, _ -> a == null },
//            { _, t, _, _, _, _, _ -> t.c == null },
//            { _, a, _, _, _, _ -> a.b == null },
//            { _, t, a, _, _, _, r -> a.foo() > 1 && t.c.x < 3 && r == 1 },
//            { _, t, a, key, _, statics, r ->
//                val hashMap = statics.extractSingleStaticMap()
//
//                val firstConditionNegation = !(a.foo() > 1 && t.c.x < 3)
//                val secondCondition = a.b.bar() < 3 && key in hashMap
//
//                firstConditionNegation && secondCondition && r == 2
//            },
//            { _, t, a, key, x, statics, r ->
//                val hashMap = statics.extractSingleStaticMap()
//
//                val firstConditionNegation = !(a.foo() > 1 && t.c.x < 3)
//                val secondConditionNegation = !(a.b.bar() < 3 && key in hashMap)
//                val thirdCondition = t.c.x > 5 && t.foo(x) < 10
//
//                firstConditionNegation && secondConditionNegation && thirdCondition && r == 3
//            },
//            { _, t, a, key, x, statics, r ->
//                val hashMap = statics.extractSingleStaticMap()
//
//                val firstConditionNegation = !(a.foo() > 1 && t.c.x < 3)
//                val secondConditionNegation = !(a.b.bar() < 3 && key in hashMap)
//                val thirdConditionNegation = !(t.c.x > 5 && t.foo(x) < 10)
//
//                firstConditionNegation && secondConditionNegation && thirdConditionNegation && r == 4
//            },
//            // TODO JIRA:1588
//        )
//    }
}