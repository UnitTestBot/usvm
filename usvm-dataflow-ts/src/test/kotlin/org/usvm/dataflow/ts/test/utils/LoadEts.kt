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
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import kotlin.io.path.toPath

fun loadEtsFileDtoFromResource(jsonPath: String): EtsFileDto {
    val sampleFilePath = object {}::class.java.getResourceAsStream(jsonPath)
        ?: error("Resource not found: $jsonPath")
    return EtsFileDto.loadFromJson(sampleFilePath)
}

fun loadEtsFileFromResource(jsonPath: String): EtsFile {
    val etsFileDto = loadEtsFileDtoFromResource(jsonPath)
    return convertToEtsFile(etsFileDto)
}

fun autoLoadEtsFileFromResource(tsPath: String): EtsFile {
    val path = object {}::class.java.getResource(tsPath)?.toURI()?.toPath()
        ?: error("Resource not found: $tsPath")
    return loadEtsFileAutoConvert(path)
}
