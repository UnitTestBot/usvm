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

package org.usvm.dataflow.ts.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.test.utils.AbcProjects
import org.usvm.dataflow.ts.test.utils.AggregatedReport
import org.usvm.dataflow.ts.test.utils.generateReportForProject
import java.io.File

@EnabledIf("projectAvailable")
class EtsTypeResolverPerformanceTest {
    companion object {
        const val RUNS = 5
        const val OUTPUT_FILE = "performance_report.md"

        @JvmStatic
        private fun projectAvailable(): Boolean {
            return AbcProjects.projectAvailable()
        }
    }

    private fun runOnAbcProject(projectID: String, abcPath: String): AggregatedReport {
        val report = generateReportForProject(projectID, abcPath, RUNS)
        return report
    }

    @Test
    fun testAbcProjects() {
        val reports = listOf(
            runOnAbcProject(
                projectID = "project1",
                abcPath = "callui-default-signed",
            ),
            runOnAbcProject(
                projectID = "project2",
                abcPath = "CertificateManager_240801_843398b",
            ),
            runOnAbcProject(
                projectID = "project3",
                abcPath = "mobiledatasettings-callui-default-signed",
            ),
            runOnAbcProject(
                projectID = "project4",
                abcPath = "Music_Demo_240727_98a3500",
            ),
            runOnAbcProject(
                projectID = "project5",
                abcPath = "phone_photos-default-signed_20240905_151755",
            ),
            runOnAbcProject(
                projectID = "project6",
                abcPath = "phone-default-signed_20240409_144519",
            ),
            runOnAbcProject(
                projectID = "project7",
                abcPath = "SecurityPrivacyCenter_240801_843998b",
            )
        )

        val file = File(OUTPUT_FILE)
        file.writeText(buildString {
            appendLine("|project|min time|max time|min %|max %|")
            appendLine("|:--|:--|:--|:--|:--|")
            reports.forEach {
                appendLine(it.dumpToString())
            }
        })
    }
}