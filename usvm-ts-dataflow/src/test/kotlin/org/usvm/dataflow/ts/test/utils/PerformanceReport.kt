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

import kotlin.time.Duration
import kotlin.time.measureTimedValue

data class PerformanceReport(
    val time: Duration,
    val statistics: TypeInferenceStatistics
)

data class AggregatedReport(
    val projectId: String,
    val max: PerformanceReport,
    val min: PerformanceReport,
) {
    fun dumpToString(): String = listOf<Any>(
        projectId,
        min.time,
        max.time,
        min.statistics.calculateImprovement(),
        max.statistics.calculateImprovement()
    ).joinToString(
        separator = "|",
        prefix = "|",
        postfix = "|"
    ) { it.toString() }
}

fun generateReportForProject(
    projectId: String,
    abcPath: String,
    runCount: Int
): AggregatedReport {
    val abcScene = AbcProjects.getAbcProject(abcPath)

    val reports = List(runCount) {
        val (statistics, time) = measureTimedValue {
            AbcProjects.runOnAbcProject(abcScene).second
        }
        PerformanceReport(time, statistics)
    }

    val maxStats = reports
        .map { it.statistics }
        .maxBy { it.calculateImprovement() }

    val minStats = reports
        .map { it.statistics }
        .minBy { it.calculateImprovement() }

    val maxReport = PerformanceReport(
        time = reports.maxOf { it.time },
        statistics = maxStats
    )

    val minReport = PerformanceReport(
        time = reports.minOf { it.time },
        statistics = minStats
    )

    return AggregatedReport(
        projectId = projectId,
        max = maxReport,
        min = minReport
    )
}