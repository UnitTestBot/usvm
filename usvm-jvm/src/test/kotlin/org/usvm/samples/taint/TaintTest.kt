package org.usvm.samples.taint

import io.ksmt.utils.cast
import org.jacodb.api.JcMethod
import org.jacodb.configuration.Argument
import org.jacodb.configuration.AssignMark
import org.jacodb.configuration.CallParameterContainsMark
import org.jacodb.configuration.ConstantBooleanValue
import org.jacodb.configuration.ConstantEq
import org.jacodb.configuration.ConstantTrue
import org.jacodb.configuration.CopyAllMarks
import org.jacodb.configuration.RemoveAllMarks
import org.jacodb.configuration.TaintCleaner
import org.jacodb.configuration.TaintConfigurationItem
import org.jacodb.configuration.TaintEntryPointSource
import org.jacodb.configuration.TaintMark
import org.jacodb.configuration.TaintMethodSink
import org.jacodb.configuration.TaintMethodSource
import org.jacodb.configuration.TaintPassThrough
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.TaintAnalysis
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.samplesWithTaintConfiguration
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jacodb.configuration.Result as ResultPos

class TaintTest : JavaMethodTestRunner() {
    override val jacodbCpKey: String
        get() = samplesWithTaintConfiguration

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

            val reachedTargets = collectedStates.single().reachedTerminalTargets.singleOrNull()

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

            val reachedTargets = collectedStates.single().reachedTerminalTargets.singleOrNull()

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

            val reachedTargets = collectedStates.single().reachedTerminalTargets.singleOrNull()

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

    private fun sampleConfiguration(): List<TaintConfigurationItem> = buildList {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val taintEntryPointSourceMethod = findMethod(sampleClassName, "taintedEntrySource")

        this += TaintEntryPointSource(
            taintEntryPointSourceMethod,
            ConstantTrue,
            listOf(AssignMark(Argument(0), sqlInjection))
        )

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val sampleCondition = ConstantEq(Argument(0), ConstantBooleanValue(true))
        this += TaintMethodSource(
            sampleSourceMethod,
            sampleCondition,
            listOf(AssignMark(ResultPos, sqlInjection))
        )
        this += TaintMethodSource(
            sampleSourceMethod,
            sampleCondition,
            listOf(AssignMark(ResultPos, sensitiveData))
        )

        val samplePassThoughMethod = findMethod("java.lang.String", "concat")
        this += TaintPassThrough(
            samplePassThoughMethod,
            ConstantTrue,
            listOf(CopyAllMarks(Argument(0), ResultPos))
        )
        this += TaintPassThrough(
            samplePassThoughMethod,
            ConstantTrue,
            listOf(CopyAllMarks(Argument(1), ResultPos))
        )

        val sampleCleanerMethod = findMethod(sampleClassName, "cleaner")
        this += TaintCleaner(
            sampleCleanerMethod,
            ConstantTrue,
            listOf(RemoveAllMarks(ResultPos))
        )

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerOfSensitiveData = findMethod(sampleClassName, "consumerOfSensitiveData")
        val consumerWithReturningValue = findMethod(sampleClassName, "consumerWithReturningValue")

        this += TaintMethodSink(
            CallParameterContainsMark(Argument(0), sqlInjection),
            consumerOfInjections
        )
        this += TaintMethodSink(
            CallParameterContainsMark(Argument(0), sqlInjection),
            consumerOfSensitiveData
        )
        this += TaintMethodSink(
            CallParameterContainsMark(Argument(0), sqlInjection),
            consumerWithReturningValue
        )
        this += TaintMethodSink(
            CallParameterContainsMark(Argument(0), sensitiveData),
            consumerWithReturningValue
        )
    }

    private fun constructCommonTaintAnalysis(): TaintAnalysis {
        val sampleClassName = "org.usvm.samples.taint.Taint"

        val configuration = sampleConfiguration()

        val consumerOfInjections = findMethod(sampleClassName, "consumerOfInjections")
        val consumerSinkRule = configuration.rules<TaintMethodSink>(consumerOfInjections).single()

        val targetForTaintedEntrySink = TaintAnalysis.TaintMethodSinkTarget(
            findMethod(sampleClassName, "taintedEntrySource")
                .instList
                .first { "consumerOfInjections" in it.toString() },
            consumerSinkRule.condition,
            consumerSinkRule
        )

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.rules<TaintMethodSource>(sampleSourceMethod).first()

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
        val consumerWithReturningValueSinkRule =
            configuration.rules<TaintMethodSink>(consumerWithReturningValue).first()

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
        val consumerSinkRule = configuration.rules<TaintMethodSink>(consumerOfInjections).single()

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.rules<TaintMethodSource>(sampleSourceMethod).first()

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
        val consumerSinkRule = configuration.rules<TaintMethodSink>(consumerOfInjections).single()

        val sampleSourceMethod = findMethod(sampleClassName, "stringProducer")
        val stringProducerRule = configuration.rules<TaintMethodSource>(sampleSourceMethod).first()

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

    private inline fun <reified T : TaintConfigurationItem> List<TaintConfigurationItem>.rules(
        method: JcMethod
    ): List<T> = filter {
        method == when (it) {
            is TaintCleaner -> it.methodInfo
            is TaintEntryPointSource -> it.methodInfo
            is TaintMethodSink -> it.methodInfo
            is TaintMethodSource -> it.methodInfo
            is TaintPassThrough -> it.methodInfo
            else -> null
        }
    }.filterIsInstance<T>()

    private val sqlInjection = TaintMark("SqlInjection")
    private val sensitiveData = TaintMark("SensitiveData")
}
