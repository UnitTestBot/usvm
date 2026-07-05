package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME

/**
 * Concrete callee resolution over an [EtsScene].
 *
 * Mirrors (in a simplified form) the name-based virtual dispatch of
 * `org.usvm.machine.interpreter.TsInterpreter.visitVirtualMethodCall`:
 * the runtime class of the receiver is looked up first, then its ancestors.
 */
internal class CallResolver(private val scene: EtsScene) {

    private val classesByName: Map<String, List<EtsClass>> by lazy {
        (scene.projectClasses + scene.sdkClasses).groupBy { it.name }
    }

    fun classByName(name: String): EtsClass? = classesByName[name]?.firstOrNull()

    fun classBySignature(signature: EtsClassSignature): EtsClass? {
        val candidates = classesByName[signature.name] ?: return null
        return candidates.firstOrNull { it.signature == signature } ?: candidates.firstOrNull()
    }

    /** Resolve an instance method by name on [cls], walking the superclass chain. */
    fun resolveInstanceMethod(cls: EtsClass, name: String): EtsMethod? {
        var current: EtsClass? = cls
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current.name)) {
            current.methods.firstOrNull { it.name == name && it.cfg.stmts.isNotEmpty() }
                ?.let { return it }
            current = current.superClass?.let { classBySignature(it) }
        }
        return null
    }

    /** Resolve a static call target (including free functions in the default class). */
    fun resolveStaticMethod(callee: EtsMethodSignature): EtsMethod? {
        val className = callee.enclosingClass.name
        val candidates = if (className.isBlank() || className == DEFAULT_ARK_CLASS_NAME) {
            // Free function: search default classes across the scene files
            classesByName[DEFAULT_ARK_CLASS_NAME].orEmpty()
        } else {
            classesByName[className].orEmpty()
        }
        for (cls in candidates) {
            cls.methods.firstOrNull { it.name == callee.name && it.cfg.stmts.isNotEmpty() }
                ?.let { return it }
        }
        // Last resort: a unique method with this name anywhere in the project
        val global = scene.projectClasses
            .flatMap { it.methods }
            .filter { it.name == callee.name && it.cfg.stmts.isNotEmpty() }
        return global.singleOrNull()
    }
}
