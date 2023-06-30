// TODO unsupported
//package org.usvm.samples.models
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.utbot.framework.plugin.api.UtArrayModel
//import org.utbot.framework.plugin.api.UtAssembleModel
//import org.utbot.framework.plugin.api.UtDirectSetFieldModel
//import org.utbot.framework.plugin.api.UtExecutionSuccess
//import org.utbot.framework.plugin.api.UtReferenceModel
//import org.usvm.test.util.checkers.eq
//import org.utbot.testing.UtModelTestCaseChecker
//
//// TODO failed Kotlin compilation SAT-1332
//internal class ModelsIdEqualityChecker : UtModelTestCaseChecker(
//    testClass = ModelsIdEqualityExample::class,
//    testCodeGeneration = true,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA),
//        TestLastStage(CodegenLanguage.KOTLIN, CodeGeneration)
//    )
//) {
//    @Test
//    fun testObjectItself() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::objectItself,
//            eq(1),
//            { _, o, r -> (o as UtReferenceModel).id == ((r as UtExecutionSuccess).model as UtReferenceModel).id }
//        )
//    }
//
//    @Test
//    fun testRefField() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::refField,
//            eq(1),
//            { _, o, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val fieldId = (o as UtAssembleModel).findFieldId()
//                fieldId == resultId
//            }
//        )
//    }
//
//    @Test
//    fun testArrayField() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::arrayField,
//            eq(1),
//            { _, o, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val fieldId = (o as UtAssembleModel).findFieldId()
//                fieldId == resultId
//            }
//        )
//    }
//
//    @Test
//    fun testArrayItself() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::arrayItself,
//            eq(1),
//            { _, o, r -> (o as? UtReferenceModel)?.id == ((r as UtExecutionSuccess).model as? UtReferenceModel)?.id }
//        )
//    }
//
//    @Test
//    fun testSubArray() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::subArray,
//            eq(1),
//            { _, array, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val arrayId = (array as UtArrayModel).findElementId(0)
//                resultId == arrayId
//            }
//        )
//    }
//
//    @Test
//    fun testSubRefArray() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::subRefArray,
//            eq(1),
//            { _, array, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val arrayId = (array as UtArrayModel).findElementId(0)
//                resultId == arrayId
//            }
//        )
//    }
//
//    @Test
//    fun testWrapperExample() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::wrapperExample,
//            eq(1),
//            { _, o, r -> (o as? UtReferenceModel)?.id == ((r as UtExecutionSuccess).model as? UtReferenceModel)?.id }
//        )
//    }
//
//    @Test
//    fun testObjectFromArray() {
//        checkExecutionMatches(
//            ModelsIdEqualityExample::objectFromArray,
//            eq(1),
//            { _, array, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val objectId = (array as UtArrayModel).findElementId(0)
//                resultId == objectId
//            }
//        )
//    }
//
//    @Test
//    fun testObjectAndStatic() {
//        checkStaticsAfter(
//            ModelsIdEqualityExample::staticSetter,
//            eq(1),
//            { _, obj, statics, r ->
//                val resultId = ((r as UtExecutionSuccess).model as UtReferenceModel).id
//                val objectId = (obj as UtReferenceModel).id
//                val staticId = (statics.values.single() as UtReferenceModel).id
//                resultId == objectId && resultId == staticId
//            }
//        )
//
//    }
//
//    private fun UtReferenceModel.findFieldId(): Int? {
//        this as UtAssembleModel
//        val fieldModel = this.modificationsChain
//            .filterIsInstance<UtDirectSetFieldModel>()
//            .single()
//            .fieldModel
//        return (fieldModel as UtReferenceModel).id
//    }
//
//    private fun UtArrayModel.findElementId(index: Int) =
//        if (index in stores.keys) {
//            (stores[index] as UtReferenceModel).id
//        } else {
//            (constModel as UtReferenceModel).id
//        }
//}