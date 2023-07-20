package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.hasAnnotation


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

fun main(args: Array<String>) {
    val testsDir = File(args.getOrElse(0) { "../Game_env/usvm-jvm/src/test/kotlin" })
    val classLoader = URLClassLoader(arrayOf(testsDir.toURI().toURL()))
    val classes = mutableListOf<Class<*>>()
    recursiveLoad(testsDir, classes, classLoader, "")
    println()
    println("LOADING COMPLETE")
    println()
    runBlocking(Dispatchers.IO) {
        val tests = mutableListOf<Job>()
        classes.forEach { cls ->
            if (cls.isAnnotationPresent(Disabled::class.java)) {
                return@forEach
            }
            if (!cls.methods.any { it.isAnnotationPresent(Test::class.java) }) {
                return@forEach
            }
            val kotlinCls = cls::kotlin.get()
            val instance = kotlinCls.createInstance()
            kotlinCls.members.forEach loop@{ method ->
                if (!method.hasAnnotation<BeforeEach>()) {
                    return@loop
                }
                try {
                    method.call(instance)
                } catch (e: Exception) {
                    println("Before each exception: $e")
                }
            }
            kotlinCls.members.forEach loop@{ method ->
                if (method.hasAnnotation<Disabled>()) {
                    return@loop
                }
                if (!method.hasAnnotation<Test>()) {
                    return@loop
                }
                val test = launch {
                    try {
                        println("Running test ${method.name}")
                        method.call(instance)
                    } catch (e: InvocationTargetException) {
                        println("InvocationTargetException: ${e.cause}")
                    } catch (e: Exception) {
                        println(e)
                    }
                }
                tests.add(test)
            }
        }
        tests.joinAll()
    }

    val dirname = "../Data/jsons/"
    val resultDirname = "../Data/"
    val resultFilename = "current_dataset.json"
    val jsons = mutableListOf<JsonElement>()

    File(dirname).listFiles()?.forEach { file ->
        if (!file.isFile || file.extension != "json") {
            return@forEach
        }
        val json = Json.decodeFromString<JsonElement>(file.readText())
        jsons.add(buildJsonObject {
            put("methodHash", file.nameWithoutExtension.hashCode())
            put("json", json)
            put("methodName", file.nameWithoutExtension)
        })
        file.delete()
    }
    jsons.sortBy { it.jsonObject["methodName"].toString() }

    if (jsons.isEmpty()) {
        return
    }
    val bigJson = buildJsonObject {
        put("scheme", jsons.first().jsonObject["json"]!!.jsonObject["scheme"]!!)
        putJsonArray("paths") {
            jsons.forEach {
                addJsonArray {
                    add(it.jsonObject["methodHash"]!!)
                    add(it.jsonObject["json"]!!.jsonObject["path"]!!)
                    add(it.jsonObject["methodName"]!!)
                }
            }
        }
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    resultFile.parentFile.mkdirs()
    resultFile.writeText(Json.encodeToString(bigJson))
}
