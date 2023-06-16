package org.usvm.samples.codegen


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("INACCESSIBLE_TYPE")
internal class ClassWithStaticAndInnerClassesTest : JavaMethodTestRunner() {
    @Test
    fun testUsePrivateStaticClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateStaticClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateStaticClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateStaticClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicStaticClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicStaticClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicInnerClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicInnerClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePackagePrivateFinalStaticClassWithPackagePrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalStaticClassWithPackagePrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePackagePrivateFinalInnerClassWithPackagePrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalInnerClassWithPackagePrivateField,
            eq(2),
        )
    }

    @Test
    fun testGetValueFromPublicFieldWithPrivateType() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::getValueFromPublicFieldWithPrivateType,
            eq(2),
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testPublicStaticClassWithPrivateField_DeepNestedStatic_g() {
//        checkAllCombinations(
//            ClassWithStaticAndInnerClasses.PublicStaticClassWithPrivateField.DeepNestedStatic::g,
//            generateWithNested = true
//        )
//    }
//
//    @Test
//    fun testPublicStaticClassWithPrivateField_DeepNested_h() {
//        checkAllCombinations(
//            ClassWithStaticAndInnerClasses.PublicStaticClassWithPrivateField.DeepNested::h,
//            generateWithNested = true
//        )
//    }
}