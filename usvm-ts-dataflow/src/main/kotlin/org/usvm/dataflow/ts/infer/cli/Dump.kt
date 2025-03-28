/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.infer.cli

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.dto.toDto
import java.nio.file.Path
import kotlin.io.path.outputStream

private val logger = KotlinLogging.logger {}

private val json = Json {
    prettyPrint = true
}

@OptIn(ExperimentalSerializationApi::class)
fun dumpTypeInferenceResult(
    result: TypeInferenceResult,
    path: Path,
    skipAnonymous: Boolean = true,
) {
    logger.info { "Dumping inferred types to '$path'" }
    val dto = result.toDto()
        // Filter out anonymous classes and methods
        .let { dto ->
            if (skipAnonymous) {
                dto.copy(
                    classes = dto.classes.filterNot { cls ->
                        cls.signature.name.startsWith(ANONYMOUS_CLASS_PREFIX)
                    },
                    methods = dto.methods.filterNot { method ->
                        method.signature.declaringClass.name.startsWith(ANONYMOUS_CLASS_PREFIX)
                            || method.signature.name.startsWith(ANONYMOUS_METHOD_PREFIX)
                    }
                )
            } else {
                dto
            }
        }
    path.outputStream().use { stream ->
        json.encodeToStream(dto, stream)
    }
}
