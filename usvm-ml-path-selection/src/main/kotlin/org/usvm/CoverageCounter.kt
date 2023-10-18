package org.usvm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

class CoverageCounter(
    private val mlConfig: MLConfig
) {
    private val testCoverages = ConcurrentHashMap<String, List<Float>>()
    private val testStatementsCounts = ConcurrentHashMap<String, Float>()
    private val testDiscounts = ConcurrentHashMap<String, List<Float>>()
    private val testFinished = ConcurrentHashMap<String, Boolean>()

    fun addTest(testName: String, statementsCount: Float) {
        testCoverages[testName] = List(mlConfig.discounts.size) { 0.0f }
        testStatementsCounts[testName] = statementsCount
        testDiscounts[testName] = List(mlConfig.discounts.size) { 1.0f }
        testFinished[testName] = false
    }

    fun updateDiscounts(testName: String) {
        testDiscounts[testName] = testDiscounts.getValue(testName)
            .mapIndexed { id, currentDiscount -> mlConfig.discounts[id] * currentDiscount }
    }

    fun updateResults(testName: String, newCoverage: Float) {
        val currentDiscounts = testDiscounts.getValue(testName)
        testCoverages[testName] = testCoverages.getValue(testName)
            .mapIndexed { id, currentCoverage -> currentCoverage + currentDiscounts[id] * newCoverage }
    }

    fun finishTest(testName: String) {
        testFinished[testName] = true
    }

    fun reset() {
        testCoverages.clear()
        testStatementsCounts.clear()
        testDiscounts.clear()
        testFinished.clear()
    }

    private fun getTotalCoverages(): List<Float> {
        return testCoverages.values.reduce { acc, floats ->
            acc.zip(floats).map { (total, value) -> total + value }
        }
    }

    @Serializable
    private data class TestStatistics(
        private val discounts: Map<String, Float>,
        private val statementsCount: Float,
        private val finished: Boolean,
    )

    @Serializable
    private data class Statistics(
        private val tests: Map<String, TestStatistics>,
        private val totalDiscounts: Map<String, Float>,
        private val totalStatementsCount: Float,
        private val finishedTestsCount: Float,
    )

    fun getStatistics(): JsonObject {
        val discountStrings = mlConfig.discounts.map { it.toString() }
        val testStatistics = testCoverages.mapValues { (test, coverages) ->
            TestStatistics(
                discountStrings.zip(coverages).toMap(),
                testStatementsCounts.getValue(test),
                testFinished.getValue(test),
            )
        }
        val statistics = Statistics(
            testStatistics,
            discountStrings.zip(getTotalCoverages()).toMap(),
            testStatementsCounts.values.sum(),
            testFinished.values.sumOf { if (it) 1.0 else 0.0 }.toFloat(),
        )
        return Json.encodeToJsonElement(statistics).jsonObject
    }
}
