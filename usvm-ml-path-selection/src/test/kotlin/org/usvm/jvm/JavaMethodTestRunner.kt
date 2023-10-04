package org.usvm.jvm

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName
import org.usvm.CoverageZone
import org.usvm.MLMachineOptions
import org.usvm.MLPathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.jvm.machine.MLJcMachine
import kotlin.io.path.Path
import kotlin.system.measureTimeMillis

fun jarLoad(jars: Set<String>, classes: MutableMap<String, MutableList<JcClassOrInterface>>) {
    jars.forEach { filePath ->
        val file = Path(filePath).toFile()
        val container = JacoDBContainer(key = filePath, classpath = listOf(file))
        val classNames = container.db.locations.flatMap { it.jcLocation?.classNames ?: listOf() }
        classes[filePath] = mutableListOf()
        classNames.forEach { className ->
            container.cp.findClassOrNull(className)?.let {
                classes[filePath]?.add(it)
            }
        }
    }
}

fun getMethodFullName(method: Any?): String {
    return if (method is JcMethod) {
        "${method.enclosingClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})"
    } else {
        method.toString()
    }
}

val baseOptions = UMachineOptions(
    coverageZone = CoverageZone.TRANSITIVE,
    exceptionsPropagation = true,
    timeoutMs = 60_000,
    stepsFromLastCovered = 3500L,
)

open class JavaMethodTestRunner {
    private val options: MLMachineOptions = MLMachineOptions(
        baseOptions,
        MLPathSelectionStrategy.GNN,
        heteroGNNModelPath = "Game_env/test_model.onnx"
    )

    fun runner(method: JcMethod, jarKey: String) {
        MLJcMachine(JacoDBContainer(jarKey).cp, options).use { machine ->
            machine.analyze(method, emptyList())
        }
    }
}


fun main() {
    val inputJars = mapOf(
        Pair("Game_env/guava-28.2-jre.jar", listOf("com.google.common"))
    )

    val jarClasses = mutableMapOf<String, MutableList<JcClassOrInterface>>()
    jarLoad(inputJars.keys, jarClasses)
    println("\nLOADING COMPLETE\n")

    jarClasses.forEach { (key, classesList) ->
        println("RUNNING TESTS FOR $key")
        val allMethods = classesList.filter { cls ->
            !cls.isAnnotation && !cls.isInterface &&
                    inputJars.getValue(key).any { cls.packageName.contains(it) } &&
                    !cls.name.contains("Test")
        }.flatMap { cls ->
            cls.declaredMethods.filter { method ->
                method.enclosingClass == cls && !method.isConstructor
            }
        }.sortedBy { getMethodFullName(it).hashCode() }.distinctBy { getMethodFullName(it) }

        println("  RUNNING TESTS WITH ${baseOptions.timeoutMs}ms TIME LIMIT")
        println("    RUNNING TESTS WITH ${MLPathSelectionStrategy.GNN} PATH SELECTOR")

        val testRunner = JavaMethodTestRunner()
        for (method in allMethods.shuffled()) {
            try {
                println("      Running test ${method.name}")
                val time = measureTimeMillis {
                    testRunner.runner(method, key)
                }
                println("      Test ${method.name} finished after ${time}ms")
            } catch (e: NotImplementedError) {
                println("      $e, ${e.message}")
            }
        }
    }
}
