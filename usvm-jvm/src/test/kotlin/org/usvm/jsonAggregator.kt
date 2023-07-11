package org.usvm

import kotlinx.serialization.encodeToString
import java.io.File
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path


fun String.runCommand() {
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(10, TimeUnit.MINUTES)
}


fun main() {
    "./gradlew test".runCommand()

    val dirname = "./usvm-jvm/paths_log/"
    val resultDirname = "${dirname}final"
    val resultFilename = "result.json"
    val jsons = mutableListOf<JsonElement>()

    File(dirname).listFiles()?.forEach { file ->
        if (!file.isFile || file.extension != "json") {
            return@forEach
        }
        jsons.add(Json.decodeFromString(file.readText()))
        file.delete()
    }

    if (jsons.isEmpty()) {
        return
    }
    val bigJson = buildJsonObject {
        put("scheme", jsons.first().jsonObject["scheme"]!!)
        putJsonArray("paths") {
            jsons.forEach {
                add(it.jsonObject["path"]!!)
            }
        }
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    resultFile.parentFile.mkdirs()
    resultFile.writeText(Json.encodeToString(bigJson))
}
