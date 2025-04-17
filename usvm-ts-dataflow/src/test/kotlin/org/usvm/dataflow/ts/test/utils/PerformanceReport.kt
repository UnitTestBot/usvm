/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.test.utils

import java.math.RoundingMode
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

data class PerformanceReport(
    val projectId: String,
    val maxTime: Duration,
    val avgTime: Duration,
    val medianTime: Duration,
    val minTime: Duration,
    val improvement: Double,
) {
    fun dumpToString(): String = listOf<Any>(
        projectId,
        minTime.toString(unit = DurationUnit.SECONDS, decimals = 3),
        maxTime.toString(unit = DurationUnit.SECONDS, decimals = 3),
        avgTime.toString(unit = DurationUnit.SECONDS, decimals = 3),
        medianTime.toString(unit = DurationUnit.SECONDS, decimals = 3),
        improvement.toBigDecimal().setScale(4, RoundingMode.HALF_UP).toDouble()
    ).joinToString(
        separator = "|",
        prefix = "|",
        postfix = "|"
    ) { it.toString() }
}

fun generateReportForProject(
    projectId: String,
    abcPath: String,
    warmupIterationsCount: Int,
    runIterationsCount: Int,
): PerformanceReport {
    val abcScene = AbcProjects.getAbcProject(abcPath)

    val results = List(warmupIterationsCount + runIterationsCount) {
        val (result, time) = measureTimedValue {
            AbcProjects.inferTypes(abcScene)
        }
        val statistics = AbcProjects.calculateStatistics(abcScene, result)
        time to statistics.calculateImprovement()
    }.drop(warmupIterationsCount)

    val times = results.map { it.first }
    val improvement = results.map { it.second }.distinct().first()
    val totalTime = times.reduce { acc, duration -> acc + duration }

    return PerformanceReport(
        projectId = projectId,
        minTime = times.min(),
        maxTime = times.max(),
        avgTime = totalTime / runIterationsCount,
        medianTime = times.sorted()[runIterationsCount / 2],
        improvement = improvement
    )
}
