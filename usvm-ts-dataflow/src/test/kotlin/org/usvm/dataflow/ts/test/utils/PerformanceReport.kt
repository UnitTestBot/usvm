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
    val projectId: String,
    val maxTime: Duration,
    val minTime: Duration,
    val improvement: Double,
) {
    fun dumpToString(): String = listOf<Any>(
        projectId,
        minTime,
        maxTime,
        improvement
    ).joinToString(
        separator = "|",
        prefix = "|",
        postfix = "|"
    ) { it.toString() }
}

fun generateReportForProject(
    projectId: String,
    abcPath: String,
    runCount: Int,
): PerformanceReport {
    val abcScene = AbcProjects.getAbcProject(abcPath)

    val results = List(runCount) {
        val (statistics, time) = measureTimedValue {
            AbcProjects.runOnAbcProject(abcScene).second
        }
        time to statistics.calculateImprovement()
    }

    val times = results.map { it.first }
    val improvement = results.map { it.second }.distinct().single()

    return PerformanceReport(
        projectId = projectId,
        minTime = times.min(),
        maxTime = times.max(),
        improvement = improvement
    )
}
