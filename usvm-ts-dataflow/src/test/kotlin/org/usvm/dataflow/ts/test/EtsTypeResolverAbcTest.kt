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

@EnabledIf("projectAvailable")
class EtsTypeResolverAbcTest {
    companion object {
        @JvmStatic
        private fun projectAvailable(): Boolean {
            return AbcProjects.projectAvailable()
        }
    }

    private fun runOnAbcProject(projectID: String, abcPath: String) {
        val scene = AbcProjects.getAbcProject(abcPath)
        val sceneStatistics = AbcProjects.runOnAbcProject(scene).second
        sceneStatistics.dumpStatistics("${projectID}_scene_comparison.txt")
    }

    @Test
    fun testLoadAbcProject1() = runOnAbcProject(
        projectID = "project1",
        abcPath = "callui-default-signed",
    )

    @Test
    fun testLoadAbcProject2() = runOnAbcProject(
        projectID = "project2",
        abcPath = "CertificateManager_240801_843398b",
    )

    @Test
    fun testLoadAbcProject3() = runOnAbcProject(
        projectID = "project3",
        abcPath = "mobiledatasettings-callui-default-signed",
    )

    @Test
    fun testLoadAbcProject4() = runOnAbcProject(
        projectID = "project4",
        abcPath = "Music_Demo_240727_98a3500",
    )

    @Test
    fun testLoadAbcProject5() = runOnAbcProject(
        projectID = "project5",
        abcPath = "phone_photos-default-signed_20240905_151755",
    )

    @Test
    fun testLoadAbcProject6() = runOnAbcProject(
        projectID = "project6",
        abcPath = "phone-default-signed_20240409_144519",
    )

    @Test
    fun testLoadAbcProject7() = runOnAbcProject(
        projectID = "project7",
        abcPath = "SecurityPrivacyCenter_240801_843998b",
    )
}
