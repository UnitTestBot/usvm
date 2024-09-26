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

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.walk

fun autoLoadEtsFileFromResource(tsPath: String): EtsFile {
    val path = getResourcePath(tsPath)
    return loadEtsFileAutoConvert(path)
}

@OptIn(ExperimentalPathApi::class)
fun loadProjectFromJsons(path: String): EtsScene {
    val paths = Paths.get(path).walk().filter { it.extension == "json" }
    val files = paths
        .map { EtsFileDto.loadFromJson(it.toFile().inputStream()) }
        .map { convertToEtsFile(it) }
        .toList()

    return EtsScene(files)
}

@OptIn(ExperimentalPathApi::class)
fun loadProjectFromAst(path: String) : EtsScene {
    val paths = Paths.get(path).walk().filter { it.extension == "ets" || it.extension == "ts" }
    val files = paths
        .map { loadEtsFileAutoConvert(it) }
        .toList()

    return EtsScene(files)
}
