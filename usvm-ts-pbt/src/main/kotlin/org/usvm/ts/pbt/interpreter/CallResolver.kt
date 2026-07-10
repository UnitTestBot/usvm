package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME

/**
 * Concrete callee resolution over an [EtsScene].
 *
 * Mirrors (in a simplified form) the name-based virtual dispatch of
 * `org.usvm.machine.interpreter.TsInterpreter.visitVirtualMethodCall`:
 * the runtime class of the receiver is looked up first, then its ancestors.
 */
internal class CallResolver(private val scene: EtsScene) {

    private val classesByName: Map<String, List<EtsClass>> by lazy {
        (scene.projectClasses + scene.sdkClasses)
            .groupBy { it.name }
            // Prefer definitions over declarations: path-alias imports produce
            // phantom classes (a signature with no method bodies) that would
            // otherwise shadow the real class of the same name.
            .mapValues { (_, classes) -> classes.sortedByDescending { it.substance() } }
    }

    private fun EtsClass.substance(): Int =
        methods.count { it.cfg.stmts.isNotEmpty() } * 2 + fields.size

    fun classByName(name: String): EtsClass? = classesByName[name]?.firstOrNull()

    fun classBySignature(signature: EtsClassSignature): EtsClass? {
        val candidates = classesByName[signature.name] ?: return null
        val exact = candidates.firstOrNull { it.signature == signature }
        // A phantom exact match loses to a substantial same-named class.
        if (exact != null && exact.substance() == 0) {
            candidates.firstOrNull { it.substance() > 0 }?.let { return it }
        }
        return exact ?: candidates.firstOrNull()
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
        // The receiver's class may be a phantom produced by a path-alias import:
        // fall back to a substantial same-named class that declares the method.
        return classesByName[cls.name].orEmpty()
            .asSequence()
            .filter { it !== cls }
            .mapNotNull { twin -> twin.methods.firstOrNull { it.name == name && it.cfg.stmts.isNotEmpty() } }
            .firstOrNull()
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

    /**
     * Variable-name -> method aliases for function values.
     *
     * Front ends lower `const f = (...) => ...` into an anonymous method
     * (`%AM0$...`) plus an assignment `f := %AM0$...` (a local of a
     * [EtsFunctionType] whose signature points to the actual method) inside the
     * file-initializer (`%dflt::%dflt`). A later `ptr_call f(...)` carries only
     * the *variable* name in its callee signature; this map recovers the target.
     */
    private val functionAliases: Map<String, EtsMethod> by lazy {
        val aliases = mutableMapOf<String, EtsMethod>()
        for (cls in scene.projectClasses) {
            if (cls.name != DEFAULT_ARK_CLASS_NAME) continue
            val fileInit = cls.methods.firstOrNull { it.name == DEFAULT_ARK_METHOD_NAME } ?: continue
            for (stmt in fileInit.cfg.stmts) {
                if (stmt !is EtsAssignStmt) continue
                val lhv = stmt.lhv as? EtsLocal ?: continue
                val rhv = stmt.rhv as? EtsLocal ?: continue
                val fnType = rhv.type as? EtsFunctionType ?: continue
                val target = cls.methods.firstOrNull { it.name == fnType.signature.name }
                    ?: classByName(fnType.signature.enclosingClass.name)
                        ?.methods?.firstOrNull { it.name == fnType.signature.name }
                if (target != null && target.cfg.stmts.isNotEmpty()) {
                    aliases.putIfAbsent(lhv.name, target)
                }
            }
        }
        aliases
    }

    /** Resolve a `ptr_call` target: by function-value alias, then by literal name. */
    fun resolveFunctionPointer(callee: EtsMethodSignature): EtsMethod? =
        functionAliases[callee.name] ?: resolveStaticMethod(callee)

    /** The method behind a file-level `const f = (...) => ...` binding, if any. */
    fun functionAliasFor(name: String): EtsMethod? = functionAliases[name]

    /**
     * Resolve the method behind a [EtsFunctionType]-typed value (a function literal:
     * the type signature carries the anonymous method name and its declaring class).
     */
    fun methodByFunctionType(type: EtsFunctionType): EtsMethod? {
        val sig = type.signature
        if (sig.name.isBlank()) return null
        val declared = classBySignature(sig.enclosingClass)
            ?.methods?.firstOrNull { it.name == sig.name && it.cfg.stmts.isNotEmpty() }
        if (declared != null) return declared
        return scene.projectClasses
            .flatMap { it.methods }
            .filter { it.name == sig.name && it.cfg.stmts.isNotEmpty() }
            .singleOrNull()
    }
}
