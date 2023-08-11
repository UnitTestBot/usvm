package org.usvm.samples.codegen

import org.usvm.samples.JavaMethodTestRunner


@Suppress("UNCHECKED_CAST")
internal class FileWithTopLevelFunctionsTest : JavaMethodTestRunner() {
    // TODO how to create a link?
   /* @Test
    fun topLevelSumTest() {
        checkExecutionMatches(
            ::topLevelSum,
        )
    }
*/
//    @Test
//    fun extensionOnBasicTypeTest() {
//        checkExecutionMatches(
//            Int::extensionOnBasicType,
//            eq(1),
//        )
//    }
//
//    @Test
//    fun extensionOnCustomClassTest() {
//        checkExecutionMatches(
//            // NB: cast is important here because we need to treat receiver as an argument to be able to check its content in matchers
//            CustomClass::extensionOnCustomClass as KFunction3<*, CustomClass, CustomClass, Boolean>,
//            eq(2),
//            { _, receiver, argument, result -> receiver === argument && result == true },
//            { _, receiver, argument, result -> receiver !== argument && result == false },
//            additionalDependencies = dependenciesForClassExtensions
//        )
//    }
//
//    companion object {
//        // Compilation of extension methods for ref objects produces call to
//        // `kotlin.jvm.internal.Intrinsics::checkNotNullParameter`, so we need to add it to dependencies
//        val dependenciesForClassExtensions = arrayOf<Class<*>>(kotlin.jvm.internal.Intrinsics::class.java)
//    }
}