package org.usvm.experiments

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.cfg.locals
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Read-only IR audit of methods observed before the ObjectUtils engine failure. */
object JavaLocalSortAudit {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 3) { "JavaLocalSortAudit <manifest> <failed-analysis> <output>" }
        val manifest = Json.parseToJsonElement(Path.of(arguments[0]).readText()).jsonObject
        val libraries = manifest.getValue("runtimeClasspath").jsonArray.map { File(it.jsonPrimitive.content) }
        val failed = Json.parseToJsonElement(Path.of(arguments[1]).readText()).jsonObject
        val rawAnalysis = failed.getValue("rawAnalysis").jsonObject
        val methods = rawAnalysis.getValue("methodSteps").jsonObject.keys
        val findings = mutableListOf<String>()

        runBlocking {
            withPilotClasspath(libraries = libraries, approximationsEnabled = true) { classpath ->
                for (signature in methods.sorted()) {
                    val owner = classpath.findClassOrNull(signature.substringBefore('#')) ?: continue
                    val name = signature.substringAfter('#').substringBefore('(')
                    val descriptor = "(" + signature.substringAfter('(')
                    val method = owner.declaredMethods.singleOrNull { it.name == name && it.description == descriptor }
                        ?: continue
                    val locals = method.instList.locals.filterIsInstance<JcLocalVar>()
                    val reusedNames = locals.groupBy { it.name }.filterValues { versions ->
                        versions.map { it.type.typeName }.distinct().size > 1
                    }
                    for ((local, versions) in reusedNames) {
                        findings += "$signature local=$local types=${versions.map { it.type.typeName }.distinct()}"
                        findings += method.instList.joinToString(separator = "\n") { it.toString() }
                    }
                }
            }
        }

        Path.of(arguments[2]).writeText(findings.joinToString(separator = "\n\n"))
        System.out.println("Audited ${methods.size} observed methods; findings=${findings.size / 2}")
    }
}
