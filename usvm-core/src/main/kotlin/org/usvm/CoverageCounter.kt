package org.usvm

object CoverageCounter {
    private val testCoverages = mutableMapOf<String, List<Float>>()
    private val testStatementsCounts = mutableMapOf<String, Float>()
    private val testDiscounts = mutableMapOf<String, List<Float>>()

    fun addTest(testName: String, statementsCount: Float) {
        testCoverages[testName] = List(MainConfig.discounts.size) { 0.0f }
        testStatementsCounts[testName] = statementsCount
        testDiscounts[testName] = List(MainConfig.discounts.size) { 1.0f }
    }

    fun updateDiscounts(testName: String) {
        testDiscounts[testName] = testDiscounts.getValue(testName)
            .mapIndexed { id, currentDiscount -> MainConfig.discounts[id] * currentDiscount }
    }

    fun updateResults(testName: String, newCoverage: Float) {
        val currentDiscounts = testDiscounts.getValue(testName)
        testCoverages[testName] = testCoverages.getValue(testName)
            .mapIndexed { id, currentCoverage -> currentCoverage + currentDiscounts[id] * newCoverage }
    }

    fun reset() {
        testCoverages.clear()
        testStatementsCounts.clear()
        testDiscounts.clear()
    }

    fun getTestCoverages(): Map<String, List<Float>> {
        return testCoverages
    }

    fun getTestStatementsCounts(): Map<String, Float> {
        return testStatementsCounts
    }

    fun getTotalCoverages(): List<Float> {
        return testCoverages.values.reduce { acc, floats -> acc.zip(floats).map { (total, value) -> total + value } }
    }
}
