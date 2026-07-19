package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.CONSTRUCTOR_NAME

/**
 * Scene-aware half of ETC v2 value materialization.
 *
 * The portable contract decoder owns recursive alias/object decoding. This
 * class supplies the operations that require EtsIR knowledge without making
 * the interpreter depend on the external-artifact or replay packages.
 */
class ConcreteSceneMaterializer(private val scene: EtsScene) {
    private val resolver = CallResolver(scene)

    fun materializeCallable(
        modulePath: String,
        exportName: String,
        callableKind: String,
    ): VFunction {
        if (callableKind !in EXACT_CALLABLE_KINDS) {
            reject("callable_kind_not_exact", "callable kind '$callableKind' is outside the exact subset")
        }
        val method = resolver.resolveCallableReference(modulePath, exportName, callableKind)
            ?: reject(
                "unresolved_callable_reference",
                "cannot resolve $modulePath#$exportName ($callableKind) in the loaded EtsIR scene",
            )
        val thisMode = when (callableKind) {
            "arrow" -> VFunctionThisMode.LEXICAL
            else -> VFunctionThisMode.DYNAMIC
        }
        return VFunction(method, VUndefined, thisMode)
    }

    /** Resolve an export only when its module is present; absence is explicit. */
    fun materializeModuleExport(modulePath: String, exportName: String): VValue {
        resolver.resolveModuleExport(modulePath, exportName)?.let { return VFunction(it) }
        if (resolver.hasDeclaredExport(modulePath, exportName)) {
            reject(
                "unresolved_module_export",
                "export $modulePath#$exportName exists but is outside concrete materialization",
            )
        }
        if (resolver.hasModule(modulePath) && !resolver.hasExactExportIndex(modulePath)) {
            reject(
                "module_export_index_unavailable",
                "module '$modulePath' has no exact export metadata",
            )
        }
        if (resolver.hasModule(modulePath)) return VUndefined
        reject("module_unavailable", "module '$modulePath' is not present in the loaded EtsIR scene")
    }

    fun construct(
        modulePath: String,
        exportName: String,
        arguments: List<VValue>,
        properties: Map<String, VValue> = emptyMap(),
    ): VObject {
        val cls = resolver.resolveConstructorClass(modulePath, exportName)
            ?: reject("constructor_unavailable", "class $modulePath#$exportName is not in the loaded scene")
        val instance = VObject(cls)
        val constructor = resolver.resolveInstanceMethod(cls, CONSTRUCTOR_NAME)
        val constructed = if (constructor == null) {
            instance
        } else {
            when (val result = EtsConcreteInterpreter(scene).execute(constructor, instance, arguments)) {
                is ExecutionResult.Returned -> (result.value as? VObject) ?: instance
                is ExecutionResult.Threw -> reject("constructor_threw", "constructor threw ${result.value}")
                is ExecutionResult.Unsupported -> reject("constructor_not_exact", result.reason)
                is ExecutionResult.Diverged -> reject("constructor_diverged", result.reason)
            }
        }
        constructed.fields.putAll(properties)
        return constructed
    }

    private fun reject(reasonCode: String, message: String): Nothing =
        throw ConcreteMaterializationException(reasonCode, message)

    private companion object {
        val EXACT_CALLABLE_KINDS = setOf("function", "arrow", "staticMethod", "instanceMethod")
    }
}

class ConcreteMaterializationException(
    val reasonCode: String,
    message: String,
) : IllegalArgumentException(message)
