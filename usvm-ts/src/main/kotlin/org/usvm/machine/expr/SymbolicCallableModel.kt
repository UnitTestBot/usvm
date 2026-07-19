package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.getGlobals
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.util.SymbolResolutionResult
import org.usvm.util.resolveImportInfo

internal sealed interface ExactSymbolicResolution {
    data object NotApplicable : ExactSymbolicResolution
    data object PendingOrRejected : ExactSymbolicResolution
    data class Resolved(val value: UExpr<out USort>) : ExactSymbolicResolution
}

/**
 * Resolve `namespace.exportedFunction` to a stable method reference.  The
 * namespace object itself is deliberately not synthesized: only a statically
 * exported callable member is part of this exact subset.
 */
internal fun TsExprResolver.tryResolveNamespaceCallable(
    field: EtsInstanceFieldRef,
): ExactSymbolicResolution {
    val namespace: EtsLocal = field.instance

    val currentFile = scope.calcOnState {
        lastEnteredMethod.enclosingClass?.declaringFile
    } ?: return ExactSymbolicResolution.NotApplicable
    val importInfo = currentFile.importInfos.singleOrNull {
        it.name == namespace.name && it.isNamespaceImport
    } ?: return ExactSymbolicResolution.NotApplicable

    // With both flags off this must be byte-for-byte legacy field resolution.
    if (!options.moduleRuntimeModel && !options.callableValueModel) {
        return ExactSymbolicResolution.NotApplicable
    }
    if (!options.moduleRuntimeModel) {
        recordSymbolicSemanticFallback(
            SymbolicSemanticReason.MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED,
            "${namespace.name}.${field.field.name}",
        )
        scope.assert(ctx.falseExpr)
        return ExactSymbolicResolution.PendingOrRejected
    }
    if (!options.callableValueModel) {
        recordSymbolicSemanticFallback(
            SymbolicSemanticReason.CALLABLE_REFERENCE_NOT_MATERIALIZED,
            "${namespace.name}.${field.field.name}",
        )
        scope.assert(ctx.falseExpr)
        return ExactSymbolicResolution.PendingOrRejected
    }

    val importedFile = when (val resolved = ctx.scene.resolveImportInfo(currentFile, importInfo)) {
        is SymbolResolutionResult.Success -> resolved.file
        is SymbolResolutionResult.FileNotFound,
        is SymbolResolutionResult.SymbolNotFound,
        -> {
            recordSymbolicSemanticFallback(
                SymbolicSemanticReason.MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED,
                "${namespace.name}.${field.field.name}:$resolved",
            )
            scope.assert(ctx.falseExpr)
            return ExactSymbolicResolution.PendingOrRejected
        }
    }

    if (!acceptStatelessModule(ctx, scope, importedFile, "${namespace.name}.${field.field.name}")) {
        return ExactSymbolicResolution.PendingOrRejected
    }

    val exportedName = importedFile.exportInfos
        .singleOrNull { it.name == field.field.name }
        ?.originalName
        ?: field.field.name
    val candidates = importedFile.callableMethods(exportedName)
    if (candidates.size != 1) {
        recordSymbolicSemanticFallback(
            SymbolicSemanticReason.MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED,
            "${namespace.name}.${field.field.name}:candidates=${candidates.size}",
        )
        scope.assert(ctx.falseExpr)
        return ExactSymbolicResolution.PendingOrRejected
    }

    val ref = scope.calcOnState { getMethodRef(candidates.single()) }
    return ExactSymbolicResolution.Resolved(ref)
}

/** Resolve a top-level/named imported function without allocating an opaque ref. */
internal fun TsSimpleValueResolver.tryResolveStableCallable(
    local: EtsLocal,
): ExactSymbolicResolution {
    require(local.type is EtsFunctionType)
    if (local.name in builtinRuntimeObjects) {
        val runtimeObject = scope.calcOnState { memory.allocConcrete(local.type) }
        return ExactSymbolicResolution.Resolved(runtimeObject)
    }
    val currentMethod = scope.calcOnState { lastEnteredMethod }
    if (local in currentMethod.getDeclaredLocals()) return ExactSymbolicResolution.NotApplicable
    val currentFile = currentMethod.enclosingClass?.declaringFile
        ?: return rejectCallable(local, "no-declaring-file")

    val importInfo = currentFile.importInfos.singleOrNull { it.name == local.name }
    val (file, exportedName) = if (importInfo != null) {
        if (!options.moduleRuntimeModel) {
            return rejectCallable(local, SymbolicSemanticReason.MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED.code)
        }
        when (val resolved = ctx.scene.resolveImportInfo(currentFile, importInfo)) {
            is SymbolResolutionResult.Success -> resolved.file to resolved.exportInfo.originalName
            is SymbolResolutionResult.FileNotFound,
            is SymbolResolutionResult.SymbolNotFound,
            -> return rejectCallable(local, resolved.toString())
        }
    } else {
        currentFile to local.name
    }

    if (options.moduleRuntimeModel && !acceptStatelessModule(ctx, scope, file, local.name)) {
        return ExactSymbolicResolution.PendingOrRejected
    }

    val candidates = file.callableMethods(exportedName)
    if (candidates.size != 1) return rejectCallable(local, "candidates=${candidates.size}")

    val ref = scope.calcOnState { getMethodRef(candidates.single()) }
    return ExactSymbolicResolution.Resolved(ref)
}

/**
 * Function declarations themselves need no runtime materialization. Stateful
 * module initializers are outside this exact subset: scheduling one while an
 * expression is being resolved would make its `undefined` return look like the
 * pending function call result. Reject those modules instead of silently
 * skipping their side effects or returning the initializer result.
 */
private fun acceptStatelessModule(
    ctx: TsContext,
    scope: TsStepScope,
    file: EtsFile,
    call: String,
): Boolean {
    // The native frontend emits the synthetic `this := this` assignment in an
    // otherwise empty module initializer; it is not observable module state.
    if (file.getGlobals().all { it.name == "this" }) return true
    recordSymbolicSemanticFallback(
        SymbolicSemanticReason.MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED,
        "$call:stateful-module-initializer",
    )
    scope.assert(ctx.falseExpr)
    return false
}

private val builtinRuntimeObjects = setOf(
    "Array",
    "Boolean",
    "Map",
    "Math",
    "Number",
    "Object",
    "Set",
    "String",
)

private fun TsSimpleValueResolver.rejectCallable(
    local: EtsLocal,
    detail: String,
): ExactSymbolicResolution.PendingOrRejected {
    recordSymbolicSemanticFallback(
        SymbolicSemanticReason.CALLABLE_REFERENCE_NOT_MATERIALIZED,
        "${local.name}:$detail",
    )
    scope.assert(ctx.falseExpr)
    return ExactSymbolicResolution.PendingOrRejected
}

private fun EtsFile.callableMethods(exportedName: String): List<EtsMethod> {
    val loweredSuffix = "\$$exportedName"
    return classes
        .asSequence()
        .flatMap { it.methods.asSequence() }
        .filter { method ->
            method.name == exportedName || method.name.endsWith(loweredSuffix)
        }
        .distinctBy { it.signature }
        .toList()
}

/**
 * Dispatch a callable stored in a fake value, such as the result of
 * `optionalCallback || importedDefault`.  Fake values contain the actual
 * address in their intermediate-ref field, so match that address against all
 * materialized method references and fork only to exact identities.  The
 * residual unknown-callable branch is intentionally absent: exact mode rejects
 * it instead of producing an unconstrained mock return.
 */
internal fun TsExprResolver.tryDispatchExactCallableValue(
    expr: EtsPtrCallExpr,
    ptr: UConcreteHeapRef,
): Boolean = with(ctx) {
    if (!options.callableValueModel || !ptr.isFakeObject()) return false

    val fakeType = ptr.getFakeType(scope)
    scope.assert(fakeType.refTypeExpr) ?: return true
    val callableRef = ptr.extractRef(scope)
    val targets = scope.calcOnState { associatedFunction.toList() }
    if (targets.isEmpty()) {
        recordSymbolicSemanticFallback(
            SymbolicSemanticReason.CALLABLE_REFERENCE_NOT_MATERIALIZED,
            expr.callee,
        )
        scope.assert(falseExpr)
        return true
    }

    val args = expr.args.map { resolve(it) ?: return true }
    val returnSite = scope.calcOnState { lastStmt }
    val exactCalls: List<Pair<UBoolExpr, TsState.() -> Unit>> = targets.map { (methodRef, function) ->
        mkHeapRefEq(callableRef, methodRef) to {
            newStmt(
                TsConcreteMethodCallStmt(
                    callee = function.method,
                    instance = function.thisInstance ?: mkUndefinedValue(),
                    args = args,
                    returnSite = returnSite,
                ),
            )
        }
    }
    recordSymbolicSemanticFallback(SymbolicSemanticReason.DYNAMIC_CALLABLE_ALTERNATIVE_REJECTED, expr.callee)
    scope.forkMulti(exactCalls)
    true
}
