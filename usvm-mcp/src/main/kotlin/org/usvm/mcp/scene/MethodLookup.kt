package org.usvm.mcp.scene

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.mcp.McpToolException

/**
 * Locating classes and methods inside an [EtsScene] with LLM-friendly errors:
 * every failure lists the actually available candidates.
 */
object MethodLookup {

    /** Returns project classes, optionally filtered by [className]. */
    fun findClasses(scene: EtsScene, className: String?): List<EtsClass> {
        if (className == null) return scene.projectClasses
        val matched = scene.projectClasses.filter { it.name == className }
        if (matched.isEmpty()) {
            val available = scene.projectClasses.joinToString(", ") { "'${it.name}'" }
            throw McpToolException(
                "Class '$className' is not found in the scene. Available classes: $available. " +
                    "Note: top-level functions live in a synthetic default class; " +
                    "omit the 'class' argument to search everywhere."
            )
        }
        return matched
    }

    /**
     * Finds a single analyzable method (a method with a non-empty CFG).
     */
    fun findMethod(scene: EtsScene, className: String?, methodName: String): EtsMethod {
        val classes = findClasses(scene, className)
        val candidates = classes
            .flatMap { it.methods }
            .filter { it.name == methodName }

        val analyzable = candidates.filter { it.isAnalyzable() }

        return when {
            analyzable.size == 1 -> analyzable.single()

            analyzable.isEmpty() && candidates.isNotEmpty() -> throw McpToolException(
                "Method '$methodName' exists but has no body (empty CFG), so it cannot be analyzed."
            )

            analyzable.isEmpty() -> {
                val available = classes
                    .flatMap { cls -> cls.methods.filter { it.isAnalyzable() } }
                    .joinToString(", ") { "'${it.enclosingClass?.name}.${it.name}'" }
                throw McpToolException(
                    "Method '$methodName' is not found. Available analyzable methods: $available. " +
                        "Use the list_methods tool to inspect the file."
                )
            }

            else -> {
                val options = analyzable.joinToString(", ") { "'${it.enclosingClass?.name}.${it.name}'" }
                throw McpToolException(
                    "Method name '$methodName' is ambiguous: $options. " +
                        "Disambiguate with the 'class' argument."
                )
            }
        }
    }

    fun EtsMethod.isAnalyzable(): Boolean =
        runCatching { cfg.stmts.isNotEmpty() }.getOrDefault(false)

    fun EtsMethod.qualifiedName(): String {
        val cls = enclosingClass?.name
        return if (cls.isNullOrBlank()) name else "$cls.$name"
    }
}
