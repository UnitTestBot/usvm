package org.usvm.samples.objects

import org.usvm.samples.JavaMethodTestRunner

internal class ClassWithClassRefTest : JavaMethodTestRunner() {

    // TODO unsupported
//    @Test
//    fun testClassRefGetName() {
//            checkWithThisAndException(
//                ClassWithClassRef::classRefName,
//                eq(2),
//                { _, instance, r -> instance.someListClass == null && r.isException<NullPointerException>() },
//                { _, instance, r -> instance.someListClass != null && r.getOrNull() == "" }, // TODO: Method coverage with `this` parameter isn't supported
//            )
//    }
}