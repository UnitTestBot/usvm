package org.usvm.samples.taint

import io.ksmt.utils.cast
import org.jacodb.api.jvm.JcField
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.Argument
import org.usvm.api.targets.AssignMark
import org.usvm.api.targets.BooleanFromArgument
import org.usvm.api.targets.CallParameterContainsMark
import org.usvm.api.targets.ConstantTrue
import org.usvm.api.targets.CopyAllMarks
import org.usvm.api.targets.JcTaintMark
import org.usvm.api.targets.RemoveAllMarks
import org.usvm.api.targets.Result
import org.usvm.api.targets.TaintAnalysis
import org.usvm.api.targets.TaintCleaner
import org.usvm.api.targets.TaintConfiguration
import org.usvm.api.targets.TaintEntryPointSource
import org.usvm.api.targets.TaintFieldSource
import org.usvm.api.targets.TaintMethodSink
import org.usvm.api.targets.TaintMethodSource
import org.usvm.api.targets.TaintPassThrough
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaintTest : JavaMethodTestRunner() {
    @UsvmTest([Options([PathSelectionStrategy.TARGETED], stopOnCoverage = -1)])
    fun testSimpleTaint(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructSimpleTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::simpleTaint,
                    ge(0),
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 1, actual = collectedStates.size)

            val reachedTargets = collectedStates.single().targets.reachedTerminal.singleOrNull()

            assertNotNull(reachedTargets)
            assertTrue { reachedTargets.isTerminal }
            assertTrue { reachedTargets.isRemoved }
            assertTrue { reachedTargets is TaintAnalysis.TaintMethodSinkTarget }
            assertTrue { reachedTargets.parent is TaintAnalysis.TaintMethodSourceTarget }
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.TARGETED])])
    fun testSimpleFalsePositive(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructCommonTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::simpleFalsePositive,
                    eq(0),
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 0, actual = collectedStates.size)
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.TARGETED])])
    fun testSimpleTruePositive(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructCommonTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::simpleTruePositive,
                    ignoreNumberOfAnalysisResults,
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 1, actual = collectedStates.size)

            val reachedTargets = collectedStates.single().targets.reachedTerminal.singleOrNull()

            assertNotNull(reachedTargets)
            assertTrue { reachedTargets.isTerminal }
            assertTrue { reachedTargets.isRemoved }
            assertTrue { reachedTargets is TaintAnalysis.TaintMethodSinkTarget }
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.TARGETED])])
    fun testTaintWithReturningValue(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructCommonTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::taintWithReturningValue,
                    ignoreNumberOfAnalysisResults
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 1, actual = collectedStates.size)

            val reachedTargets = collectedStates.single().targets.reachedTerminal.singleOrNull()

            assertNotNull(reachedTargets)
            assertTrue { reachedTargets.isTerminal }
            assertTrue { reachedTargets.isRemoved }
            assertTrue { reachedTargets is TaintAnalysis.TaintMethodSinkTarget }
            assertTrue { reachedTargets.parent is TaintAnalysis.TaintMethodSourceTarget }
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.TARGETED])])
    fun testGoThroughCleaner(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructCommonTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::goThroughCleaner,
                    ignoreNumberOfAnalysisResults
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 0, actual = collectedStates.size)
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.TARGETED], targetSearchDepth = 1u, stopOnCoverage = -1)])
    fun testFalsePositiveWithExplosion(options: UMachineOptions) {
        withOptions(options) {
            val sampleAnalysis = constructFalsePositiveWithExplosionTaintAnalysis()

            withTargets(sampleAnalysis.targets.toList().cast(), sampleAnalysis) {
                checkDiscoveredProperties(
                    Taint::falsePositiveWithExplosion,
                    eq(0),
                )
            }

            val collectedStates = sampleAnalysis.collectedStates
            assertEquals(expected = 0, actual = collectedStates.size)
        }
    }

    private fun findMethod(className: String, methodName: String) = cp
        .findClassOrNull(className)!!
        .declaredMethods
        .first { it.name == methodName }

    private fun sampleConfiguration(): TaintConfiguration {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val taintEntryPointSourceMethod = findMethod(sampleClassName, "taintedEntrySource")
        val taintEntryPointSourceCondition = ConstantTrue

        val sampleEntryPointsSources = mapOf(
            taintEntryPointSourceMethod to listOf(
                TaintEntryPointSource(
                    taintEntryPointSourceMethod,
                    taintEntryPointSourceCondition, AssignMark(Argument(0u), SqlInjection)
                )
            )
        )

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val sampleCondition = BooleanFromArgument(Argument(0u))
        val sampleMethodSources = mapOf(
            sampleSourceMethod to listOf(
                TaintMethodSource(
                    sampleSourceMethod,
                    sampleCondition, AssignMark(Result, SqlInjection)
                ),
                TaintMethodSource(
                    sampleSourceMethod,
                    sampleCondition, AssignMark(Result, SensitiveData)
                ),
            )
        )

        // TODO
        val sampleFieldSources = emptyMap<JcField, List<TaintFieldSource>>()


        val samplePassThoughMethod = findMethod("java.lang.String", "concat")
        val samplePassThroughCondition = ConstantTrue
        val samplePassThrough = mapOf(
            samplePassThoughMethod to listOf(
                TaintPassThrough(
                    samplePassThoughMethod,
                    samplePassThroughCondition, CopyAllMarks(Argument(0u), Result)
                ),
                TaintPassThrough(
                    samplePassThoughMethod,
                    samplePassThroughCondition, CopyAllMarks(Argument(1u), Result)
                ),
            )
        )

        val sampleCleanerMethod = findMethod(sampleClassName, "cleaner")
        val cleanerCondition = ConstantTrue
        val sampleCleaners = mapOf(
            sampleCleanerMethod to listOf(
                TaintCleaner(
                    sampleCleanerMethod,
                    cleanerCondition, RemoveAllMarks(Result)
                )
            )
        )

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerOfSensitiveData = findMethod(sampleClassName, "consumerOfSensitiveData")
        val consumerWithReturningValue = findMethod(sampleClassName, "consumerWithReturningValue")

        val sampleSinks = mapOf(
            consumerOfInjections to listOf(
                TaintMethodSink(
                    CallParameterContainsMark(Argument(0u), SqlInjection),
                    consumerOfInjections
                )
            ),
            consumerOfSensitiveData to listOf(
                TaintMethodSink(
                    CallParameterContainsMark(Argument(0u), SqlInjection),
                    consumerOfSensitiveData
                )
            ),
            consumerWithReturningValue to listOf(
                TaintMethodSink(CallParameterContainsMark(Argument(0u), SqlInjection), consumerWithReturningValue),
                TaintMethodSink(CallParameterContainsMark(Argument(0u), SensitiveData), consumerWithReturningValue)
            ),
        )

        return TaintConfiguration(
            sampleEntryPointsSources,
            sampleMethodSources,
            sampleFieldSources,
            samplePassThrough,
            sampleCleaners,
            sampleSinks,
            emptyMap() // TODO field sinks
        )
    }

    private fun constructCommonTaintAnalysis(): TaintAnalysis {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val configuration = sampleConfiguration()

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerSinkRule = configuration.methodSinks[consumerOfInjections]!!.single()

        val targetForTaintedEntrySink = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "taintedEntrySource")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.methodSources[sampleSourceMethod]!!.first()

        val sourceTargetForFalsePositive = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "simpleFalsePositive")
                .instList
                .first { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val intermediateTarget = TaintAnalysis.TaintIntermediateTarget(
            findMethod(sampleClassName, "simpleFalsePositive")
                .instList
                .first { "[0]" in it.toString() },
        )

        val secondIntermediateTarget = TaintAnalysis.TaintIntermediateTarget(
            findMethod(sampleClassName, "simpleFalsePositive")
                .instList
                .first { "[1]" in it.toString() },
        )

        val sinkTargetForFalsePositive = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "simpleFalsePositive")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        secondIntermediateTarget.addChild(sinkTargetForFalsePositive)
        intermediateTarget.addChild(secondIntermediateTarget)
        sourceTargetForFalsePositive.addChild(intermediateTarget)

        val sourceTargetForTruePositive = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "simpleTruePositive")
                .instList
                .first { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val intermediateTargetTruePositive = TaintAnalysis.TaintIntermediateTarget(
            findMethod(sampleClassName, "simpleTruePositive")
                .instList
                .first { "[0]" in it.toString() },
        )

        val secondIntermediateTargetTruePositive = TaintAnalysis.TaintIntermediateTarget(
            findMethod(sampleClassName, "simpleTruePositive")
                .instList
                .first { "[1]" in it.toString() },
        )

        val sinkTargetForTruePositive = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "simpleTruePositive")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        secondIntermediateTargetTruePositive.addChild(sinkTargetForTruePositive)
        intermediateTargetTruePositive.addChild(secondIntermediateTargetTruePositive)
        sourceTargetForTruePositive.addChild(intermediateTargetTruePositive)


        val sourceTaintWithReturningValue = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "taintWithReturningValue")
                .instList
                .first { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val consumerWithReturningValue = findMethod(sampleClassName, "consumerWithReturningValue")
        val consumerWithReturningValueSinkRule = configuration.methodSinks[consumerWithReturningValue]!!.first()

        val sinkTaintWithRetuningValue = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "taintWithReturningValue")
                .instList
                .first { "consumerWithReturningValue" in it.toString() },
            consumerWithReturningValueSinkRule.condition,
            consumerWithReturningValueSinkRule
        )

        sourceTaintWithReturningValue.addChild(sinkTaintWithRetuningValue)


        val sourceTaintGoThroughCleaner = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "goThroughCleaner")
                .instList
                .first { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val sinkTaintGoThroughCleaner = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "goThroughCleaner")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        sourceTaintGoThroughCleaner.addChild(sinkTaintGoThroughCleaner)

        return TaintAnalysis(configuration)
            .addTarget(targetForTaintedEntrySink)
            .addTarget(sourceTargetForFalsePositive)
            .addTarget(sourceTargetForTruePositive)
            .addTarget(sourceTaintWithReturningValue)
            .addTarget(sourceTaintGoThroughCleaner)
    }

    private fun constructSimpleTaintAnalysis(): TaintAnalysis {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val configuration = sampleConfiguration()

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerSinkRule = configuration.methodSinks[consumerOfInjections]!!.single()

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.methodSources[sampleSourceMethod]!!.first()

        val sourceTargetForSimpleTaint = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "simpleTaint")
                .instList
                .last { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val sinkTargetForSimpleTaint = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "simpleTaint")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )
        sourceTargetForSimpleTaint.addChild(sinkTargetForSimpleTaint)

        return TaintAnalysis(configuration)
            .addTarget(sourceTargetForSimpleTaint)
    }

    private fun constructFalsePositiveWithExplosionTaintAnalysis(): TaintAnalysis {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val configuration = sampleConfiguration()

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerSinkRule = configuration.methodSinks[consumerOfInjections]!!.single()

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.methodSources[sampleSourceMethod]!!.first()

        val sourceTargetForFalsePositiveWithExplosion = TaintAnalysis.TaintMethodSourceTarget(
            findMethod(sampleClassName, "falsePositiveWithExplosion")
                .instList
                .first { "stringProducer" in it.toString() },
            stringProducerRule.condition,
            stringProducerRule
        )

        val sinkTargetForFalsePositiveWithExplosion = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "falsePositiveWithExplosion")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        sourceTargetForFalsePositiveWithExplosion.addChild(sinkTargetForFalsePositiveWithExplosion)

        return TaintAnalysis(configuration)
            .addTarget(sourceTargetForFalsePositiveWithExplosion)
    }

    private object SqlInjection : JcTaintMark
    private object SensitiveData : JcTaintMark
}
