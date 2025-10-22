@file:OptIn(ExperimentalSerializationApi::class)

package org.usvm.api.reachability.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.util.io.inputStream
import java.nio.file.Path

fun parseTargetsContainer(path: Path): TargetsContainerDto {
    path.inputStream().use { stream ->
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        return json.decodeFromStream(stream)
    }
}
