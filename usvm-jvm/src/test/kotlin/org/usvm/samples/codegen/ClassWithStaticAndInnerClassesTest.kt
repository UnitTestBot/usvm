package org.usvm.samples.codegen


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("INACCESSIBLE_TYPE")
internal class ClassWithStaticAndInnerClassesTest : JavaMethodTestRunner() {
    @Test
    fun testUsePrivateStaticClassWithPrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePrivateStaticClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateStaticClassWithPublicField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePrivateStaticClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicStaticClassWithPrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicStaticClassWithPublicField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePublicStaticClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePrivateInnerClassWithPublicField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePrivateInnerClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicInnerClassWithPrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePublicInnerClassWithPublicField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePublicInnerClassWithPublicField,
            eq(2),
        )
    }

    @Test
    fun testUsePackagePrivateFinalStaticClassWithPackagePrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalStaticClassWithPackagePrivateField,
            eq(2),
        )
    }

    @Test
    fun testUsePackagePrivateFinalInnerClassWithPackagePrivateField() {
        checkDiscoveredProperties(
            ClassWithStaticAndInnerClasses::usePackagePrivateFinalInnerClassWithPackagePrivateField,
            eq(2),
        )
    }

    @Test
    fun testGetValueFromPublicFieldWithPrivateType() {
        this.checkDiscoveredProperties(
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