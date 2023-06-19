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
        )
    }

    @Test
    fun testUsePrivateStaticClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateStaticClassWithPublicField,
        )
    }

    @Test
    fun testUsePublicStaticClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPrivateField,
        )
    }

    @Test
    fun testUsePublicStaticClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPublicField,
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPrivateField,
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPublicField,
        )
    }

    @Test
    fun testUsePublicInnerClassWithPrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPrivateField,
        )
    }

    @Test
    fun testUsePublicInnerClassWithPublicField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPublicField,
        )
    }

    @Test
    fun testUsePackagePrivateFinalStaticClassWithPackagePrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalStaticClassWithPackagePrivateField,
        )
    }

    @Test
    fun testUsePackagePrivateFinalInnerClassWithPackagePrivateField() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalInnerClassWithPackagePrivateField,
        )
    }

    @Test
    fun testGetValueFromPublicFieldWithPrivateType() {
        checkExecutionMatches(
            ClassWithStaticAndInnerClasses::getValueFromPublicFieldWithPrivateType,
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