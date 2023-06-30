// TODO unsupported
//package org.usvm.samples.models
//
//import org.junit.Test
//
//import org.utbot.framework.plugin.api.FieldId
//import org.utbot.framework.plugin.api.UtAssembleModel
//import org.utbot.framework.plugin.api.UtCompositeModel
//import org.utbot.framework.plugin.api.UtModel
//import org.utbot.framework.plugin.api.UtReferenceModel
//import org.usvm.test.util.checkers.eq
//import org.utbot.testing.UtModelTestCaseChecker
//
//internal class CompositeModelMinimizationChecker : UtModelTestCaseChecker(
//    testClass = CompositeModelMinimizationExample::class,
//    testCodeGeneration = true,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA),
//        TestLastStage(CodegenLanguage.KOTLIN, CodeGeneration)
//    )
//) {
//    private fun UtModel.getFieldsOrNull(): Map<FieldId, UtModel>? = when(this) {
//        is UtAssembleModel -> origin?.fields
//        is UtCompositeModel -> fields
//        else -> null
//    }
//
//    private fun UtModel.hasInitializedFields(): Boolean = getFieldsOrNull()?.isNotEmpty() == true
//    private fun UtModel.isNotInitialized(): Boolean = getFieldsOrNull()?.isEmpty() == true
//
//    @Test
//    fun singleNotNullArgumentInitializationRequiredTest() {
//        checkExecutionMatches(
//            CompositeModelMinimizationExample::singleNotNullArgumentInitializationRequired,
//            eq(2),
//            { _, o, _ -> o.hasInitializedFields() }
//        )
//    }
//
//    @Test
//    fun sameArgumentsInitializationRequiredTest() {
//        checkExecutionMatches(
//            CompositeModelMinimizationExample::sameArgumentsInitializationRequired,
//            eq(3),
//            { _, a, b, _ ->
//                a as UtReferenceModel
//                b as UtReferenceModel
//                a.id == b.id && a.hasInitializedFields() && b.hasInitializedFields()
//            }
//        )
//    }
//
//    @Test
//    fun distinctNotNullArgumentsSecondInitializationNotExpected() {
//        checkExecutionMatches(
//            CompositeModelMinimizationExample::distinctNotNullArgumentsSecondInitializationNotExpected,
//            eq(2),
//            { _, a, b, _ ->
//                a as UtReferenceModel
//                b as UtReferenceModel
//                a.hasInitializedFields() && b.isNotInitialized()
//            }
//        )
//    }
//
//    @Test
//    fun distinctNotNullArgumentsInitializationRequired() {
//        checkExecutionMatches(
//            CompositeModelMinimizationExample::distinctNotNullArgumentsInitializationRequired,
//            eq(2),
//            { _, a, b, _ ->
//                a as UtReferenceModel
//                b as UtReferenceModel
//                a.hasInitializedFields() && b.hasInitializedFields()
//            }
//        )
//    }
//}