package org.usvm

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.lang.reflect.InvocationTargetException
import kotlin.io.path.Path


fun recursiveLoad(currentDir: File, classes: MutableList<Class<*>>, classLoader: ClassLoader, path: String) {
    currentDir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            recursiveLoad(file, classes, classLoader, "${path}${if (path == "") "" else "."}${file.name}")
        }
        if (file.isFile && file.extension == "kt") {
            try {
                classes.add(classLoader.loadClass("${path}.${file.nameWithoutExtension}"))
            } catch (e: Exception) {
                println(e)
            }
        }
    }
}

fun main() {
    val samplesDir = File("./usvm-jvm/src/test/kotlin")
    println(File("").absoluteFile)
    val classLoader = URLClassLoader(arrayOf(samplesDir.toURI().toURL()))
    val classes = mutableListOf<Class<*>>()
    recursiveLoad(samplesDir, classes, classLoader, "")
    println()
    println("LOADING COMPLETE")
    println()
    classes.forEach { cls ->
        if (!cls.methods.any { it.isAnnotationPresent(Test::class.java) }) {
            return@forEach
        }
        val instance = cls.getDeclaredConstructor().newInstance()
        cls.methods.forEach loop@ { method ->
            if (method.annotations.isEmpty()) {
                return@loop
            }
            if (method.annotations.map { it.annotationClass.simpleName }.contains("BeforeEach")) {
                method.invoke(instance)
            }
        }
        cls.methods.forEach loop@ { method ->
            if (method.declaringClass != cls) {
                return@loop
            }
            if (method.annotations.isEmpty()) {
                return@loop
            }
            if (method.isAnnotationPresent(Disabled::class.java)) {
                return@loop
            }
            if (!method.isAnnotationPresent(Test::class.java)) {
                return@loop
            }
            try {
                method.invoke(instance)
            } catch (e: InvocationTargetException) {
                println("InvocationTargetException: ${e.cause}")
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    val dirname = "./paths_log/"
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
