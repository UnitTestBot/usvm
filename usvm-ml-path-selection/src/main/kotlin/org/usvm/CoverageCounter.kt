package org.usvm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    fun getStatistics(): JsonObject {
        return buildJsonObject {
            putJsonObject("Tests") {
                testCoverages.forEach { (test, coverages) ->
                    putJsonObject(test) {
                        putJsonObject("discounts") {
                            mlConfig.discounts.zip(coverages).forEach { (discount, coverage) ->
                                put(discount.toString(), coverage)
                            }
                        }
                        put("statementsCount", testStatementsCounts[test])
                        put("finished", testFinished[test])
                    }
                }
            }
            putJsonObject("totalDiscounts") {
                 mlConfig.discounts.zip(getTotalCoverages()).forEach { (discount, coverage) ->
                    put(discount.toString(), coverage)
                }
            }
            put("totalStatementsCount", testStatementsCounts.values.sum())
            put("finishedTestsCount", testFinished.values.sumOf { if (it) 1.0 else 0.0 })
        }
    }
}
