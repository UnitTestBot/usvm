package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNewExpr
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

    private val defaultClasses: List<EtsClass> by lazy {
        classesByName[DEFAULT_ARK_CLASS_NAME].orEmpty()
    }

    private val commonJsExportIndexes: Map<String, Map<String, String>> by lazy {
        defaultClasses.mapNotNull { cls ->
            commonJsExportIndex(cls)?.let { index -> cls.signature.file.fileName to index }
        }.groupBy({ it.first }, { it.second })
            .mapNotNull { (fileName, indexes) ->
                indexes.singleOrNull()?.let { fileName to it }
            }.toMap()
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
            current.methods.firstOrNull { it.name == name && !it.isStatic && it.cfg.stmts.isNotEmpty() }
                ?.let { return it }
            current = current.superClass?.let { classBySignature(it) }
        }
        // The receiver's class may be a phantom produced by a path-alias import:
        // fall back to a substantial same-named class that declares the method.
        return classesByName[cls.name].orEmpty()
            .asSequence()
            .filter { it !== cls }
            .mapNotNull { twin ->
                twin.methods.firstOrNull { it.name == name && !it.isStatic && it.cfg.stmts.isNotEmpty() }
            }
            .firstOrNull()
    }

    /** Resolve a static call target (including free functions in the default class). */
    fun resolveStaticMethod(callee: EtsMethodSignature): EtsMethod? {
        val className = callee.enclosingClass.name
        val freeFunction = className.isBlank() || className == DEFAULT_ARK_CLASS_NAME
        fun EtsMethod.isEligible(): Boolean =
            name == callee.name && cfg.stmts.isNotEmpty() && (freeFunction || isStatic)

        classBySignature(callee.enclosingClass)
            ?.methods
            ?.firstOrNull(EtsMethod::isEligible)
            ?.let { return it }
        val candidates = if (freeFunction) {
            // Free function: search default classes across the scene files
            classesByName[DEFAULT_ARK_CLASS_NAME].orEmpty()
        } else {
            classesByName[className].orEmpty()
        }
        for (cls in candidates) {
            cls.methods.firstOrNull(EtsMethod::isEligible)
                ?.let { return it }
        }
        if (freeFunction) return null
        // Last resort: a unique method with this name anywhere in the project
        val global = scene.projectClasses
            .flatMap { it.methods }
            .filter(EtsMethod::isEligible)
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
        val candidates = mutableMapOf<String, MutableList<EtsMethod>>()
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
                    candidates.getOrPut(lhv.name) { mutableListOf() }.add(target)
                }
            }
        }
        candidates.mapNotNull { (name, methods) ->
            methods.distinctBy { it.signature }.singleOrNull()?.let { name to it }
        }.toMap()
    }

    private val functionAliasesByFile: Map<Pair<String, String>, EtsMethod> by lazy {
        val candidates = mutableMapOf<Pair<String, String>, MutableList<EtsMethod>>()
        for (cls in defaultClasses) {
            val fileInit = cls.methods.firstOrNull { it.name == DEFAULT_ARK_METHOD_NAME } ?: continue
            for (stmt in fileInit.cfg.stmts) {
                if (stmt !is EtsAssignStmt) continue
                val lhv = stmt.lhv as? EtsLocal ?: continue
                val rhv = stmt.rhv as? EtsLocal ?: continue
                val fnType = rhv.type as? EtsFunctionType ?: continue
                val target = methodByFunctionType(fnType) ?: continue
                candidates.getOrPut(cls.signature.file.fileName to lhv.name) { mutableListOf() }.add(target)
            }
        }
        candidates.mapNotNull { (binding, methods) ->
            methods.distinctBy { it.signature }.singleOrNull()?.let { binding to it }
        }.toMap()
    }

    /** Resolve a `ptr_call` target: by function-value alias, then by literal name. */
    fun resolveFunctionPointer(callee: EtsMethodSignature): EtsMethod? =
        functionAliasesByFile[callee.enclosingClass.file.fileName to callee.name]
            ?: functionAliases[callee.name]
            ?: resolveStaticMethod(callee)

    /** The method behind a file-level `const f = (...) => ...` binding, if any. */
    fun functionAliasFor(name: String, fileName: String): EtsMethod? =
        functionAliasesByFile[fileName to name] ?: functionAliases[name]

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

    /** Resolve only an import-info-backed namespace binding; bare package ids remain valid module ids. */
    fun modulePathOf(local: EtsLocal, enclosingFileName: String): String? = scene.projectFiles
        .filter { it.signature.fileName == enclosingFileName }
        .singleOrNull()
        ?.importInfos
        ?.filter { it.name == local.name && it.isNamespaceImport }
        ?.singleOrNull()
        ?.from
        ?.takeIf(String::isNotBlank)

    /** Resolve an exported function/arrow from an already materialized namespace. */
    fun resolveModuleExport(modulePath: String, exportName: String): EtsMethod? {
        val cls = moduleDefaultClass(modulePath) ?: return null
        val internalName = directExportName(cls, exportName) ?: return null
        cls.methods.firstOrNull { it.name == internalName && it.cfg.stmts.isNotEmpty() }
            ?.let { return it }
        return functionAliasesByFile[cls.signature.file.fileName to internalName]
    }

    /** True means the namespace itself resolved, so a missing member is explicitly absent. */
    fun hasModule(modulePath: String): Boolean = moduleDefaultClass(modulePath) != null

    /** Export metadata must be present before absence can be distinguished from an unknown namespace shape. */
    fun hasExactExportIndex(modulePath: String): Boolean =
        moduleDefaultClass(modulePath)?.let { cls ->
            cls.declaringFile?.exportInfos?.let { exports ->
                exports.isNotEmpty() && exports.none { it.isReExport }
            } == true ||
                commonJsExportIndexes.containsKey(cls.signature.file.fileName)
        } == true

    /** True for an export that exists in metadata but cannot be materialized by the exact subset. */
    fun hasDeclaredExport(modulePath: String, exportName: String): Boolean {
        val cls = moduleDefaultClass(modulePath) ?: return false
        val exports = cls.declaringFile?.exportInfos.orEmpty()
        return exports.any { it.name == exportName } ||
            commonJsExportIndexes[cls.signature.file.fileName]?.containsKey(exportName) == true
    }

    /**
     * Resolve a frozen ETC v2 callable reference without guessing dynamic values.
     * The caller is responsible for rejecting unsupported callable kinds.
     */
    fun resolveCallableReference(
        modulePath: String,
        exportName: String,
        callableKind: String,
    ): EtsMethod? = when (callableKind) {
        "function" -> exportedBinding(modulePath, exportName)?.let { (cls, internalName) ->
            cls.methods.firstOrNull { it.name == internalName && it.cfg.stmts.isNotEmpty() }
        }

        "arrow" -> exportedBinding(modulePath, exportName)?.let { (cls, internalName) ->
            functionAliasesByFile[cls.signature.file.fileName to internalName]
        }

        "staticMethod" -> {
            val className = exportName.substringBeforeLast('.', missingDelimiterValue = "")
            val methodName = exportName.substringAfterLast('.')
            classInModule(modulePath, className)
                ?.methods
                ?.firstOrNull { it.name == methodName && it.isStatic && it.cfg.stmts.isNotEmpty() }
        }

        "instanceMethod" -> {
            val className = exportName.substringBefore(".prototype.", missingDelimiterValue = "")
            val methodName = exportName.substringAfter(".prototype.", missingDelimiterValue = "")
            classInModule(modulePath, className)?.let { resolveInstanceMethod(it, methodName) }
        }

        else -> null
    }

    fun resolveConstructorClass(modulePath: String, exportName: String): EtsClass? =
        classInModule(modulePath, exportName)

    private fun classInModule(modulePath: String, className: String): EtsClass? {
        if (className.isBlank()) return null
        val moduleClass = moduleDefaultClass(modulePath) ?: return null
        val internalName = directExportName(moduleClass, className) ?: return null
        val fileName = moduleClass.signature.file.fileName
        return classesByName[internalName].orEmpty().firstOrNull { it.signature.file.fileName == fileName }
    }

    private fun exportedBinding(modulePath: String, exportName: String): Pair<EtsClass, String>? {
        val cls = moduleDefaultClass(modulePath) ?: return null
        val internalName = directExportName(cls, exportName) ?: return null
        return cls to internalName
    }

    /**
     * Translate a public export name to the declaration name. Empty export
     * metadata is not an export index: guessing by declaration name can expose
     * private bindings and is therefore rejected by the exact materializer.
     */
    private fun directExportName(cls: EtsClass, exportName: String): String? {
        val exports = cls.declaringFile?.exportInfos.orEmpty()
        if (exports.isEmpty()) {
            return commonJsExportIndexes[cls.signature.file.fileName]?.get(exportName)
        }
        val export = exports.filter { it.name == exportName && !it.isReExport }.singleOrNull() ?: return null
        return export.originalName
    }

    /**
     * Exact CommonJS subset used by the frozen callable fixtures:
     * `module.exports = Object.freeze({ publicName: localBinding, ... })`.
     * The object assigned to `module.exports` is traced through local aliases;
     * unrelated object literals are never treated as public exports.
     */
    private fun commonJsExportIndex(cls: EtsClass): Map<String, String>? {
        val init = cls.methods.firstOrNull { it.name == DEFAULT_ARK_METHOD_NAME } ?: return null
        val assignments = init.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        val moduleExportAssignments = assignments.mapNotNull { stmt ->
            val target = stmt.lhv as? EtsInstanceFieldRef ?: return@mapNotNull null
            val instance = target.instance as? EtsLocal ?: return@mapNotNull null
            if (instance.name != "module" || target.field.name != "exports") {
                return@mapNotNull null
            }
            val exportedLocal = (stmt.rhv as? EtsLocal)?.name ?: return@mapNotNull null
            stmt to exportedLocal
        }
        val (moduleExportAssignment, exportedLocal) = moduleExportAssignments.singleOrNull() ?: return null
        var objectLocal = exportedLocal
        val assignmentsByLocal = assignments.mapNotNull { stmt ->
            val target = stmt.lhv as? EtsLocal ?: return@mapNotNull null
            target.name to stmt
        }.groupBy({ it.first }, { it.second })
        val visited = mutableSetOf<String>()
        var passedExactFreeze = false
        var freezeAssignment: EtsAssignStmt? = null
        var rootAssignment: EtsAssignStmt? = null
        while (true) {
            if (!visited.add(objectLocal)) return null
            val sourceAssignment = assignmentsByLocal[objectLocal].orEmpty().singleOrNull() ?: break
            when (val value = sourceAssignment.rhv) {
                is EtsLocal -> objectLocal = value.name
                is EtsCallExpr -> {
                    val owner = value.callee.enclosingClass.name
                    val exactObjectReceiver = owner == "Object" ||
                        (
                            owner.isBlank() &&
                                init.locals.any { it.name == "Object" } &&
                                assignments.none { (it.lhv as? EtsLocal)?.name == "Object" }
                            )
                    if (!exactObjectReceiver || value.callee.name != "freeze") {
                        return null
                    }
                    objectLocal = (value.args.singleOrNull() as? EtsLocal)?.name ?: return null
                    passedExactFreeze = true
                    freezeAssignment = sourceAssignment
                }

                else -> {
                    rootAssignment = sourceAssignment
                    break
                }
            }
        }
        if (!passedExactFreeze) return null
        val root = rootAssignment ?: return null
        val rootType = (root.rhv as? EtsNewExpr)?.type as? EtsClassType ?: return null
        if (!rootType.signature.name.startsWith("%AC")) return null
        val rootIndex = assignments.indexOf(root)
        val freezeIndex = assignments.indexOf(freezeAssignment)
        val moduleExportIndex = assignments.indexOf(moduleExportAssignment)
        if (rootIndex < 0 || freezeIndex <= rootIndex || moduleExportIndex <= freezeIndex) return null
        val exportedProperties = assignments.mapNotNull { stmt ->
            val target = stmt.lhv as? EtsInstanceFieldRef ?: return@mapNotNull null
            val instance = target.instance as? EtsLocal ?: return@mapNotNull null
            if (instance.name != objectLocal) return@mapNotNull null
            Triple(assignments.indexOf(stmt), target.field.name, (stmt.rhv as? EtsLocal)?.name)
        }
        if (exportedProperties.any { (index, _, binding) ->
                binding == null || index <= rootIndex || index >= freezeIndex
            }
        ) {
            return null
        }
        if (exportedProperties.groupingBy { it.second }.eachCount().any { it.value != 1 }) return null
        return exportedProperties.associate { (_, name, binding) -> name to checkNotNull(binding) }
    }

    private fun moduleDefaultClass(modulePath: String): EtsClass? {
        val requested = normalizeModulePath(modulePath)
        val candidates = defaultClasses.map { cls -> cls to normalizeModulePath(cls.signature.file.fileName) }
        candidates.filter { (_, candidate) -> candidate == requested }
            .singleOrNull()
            ?.let { return it.first }
        return candidates.filter { (_, candidate) ->
            candidate.endsWith("/$requested") || requested.endsWith("/$candidate")
        }.singleOrNull()?.first
    }

    private fun normalizeModulePath(path: String): String = path
        .replace('\\', '/')
        .removeSuffix(".d.ts")
        .removeSuffix(".ts")
        .removeSuffix(".ets")
        .removeSuffix(".js")
        .removePrefix("./")
}
