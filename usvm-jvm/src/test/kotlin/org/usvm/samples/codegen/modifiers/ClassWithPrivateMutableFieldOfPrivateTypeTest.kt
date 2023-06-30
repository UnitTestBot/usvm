package org.usvm.samples.codegen.modifiers

// TODO unsupported matchers
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.test.util.checkers.eq

//import org.usvm.test.util.checkers.eq
//
//
//// TODO failed Kotlin tests execution with non-nullable expected field
//class ClassWithPrivateMutableFieldOfPrivateTypeTest : JavaMethodTestRunner() {
//    @Test
//    fun testChangePrivateMutableFieldWithPrivateType() {
//        checkAllMutationsWithThis(
//            ClassWithPrivateMutableFieldOfPrivateType::changePrivateMutableFieldWithPrivateType,
//            eq(1),
//            { thisBefore, _, thisAfter, _, r ->
//                val privateMutableField = FieldId(
//                    ClassWithPrivateMutableFieldOfPrivateType::class.id,
//                    "privateMutableField"
//                ).jField
//
//                val (privateFieldBeforeValue, privateFieldAfterValue) = privateMutableField.withAccessibility {
//                    privateMutableField.get(thisBefore) to privateMutableField.get(thisAfter)
//                }
//
//                privateFieldBeforeValue == null && privateFieldAfterValue != null && r == 0
//            }
//        )
//    }
//}
