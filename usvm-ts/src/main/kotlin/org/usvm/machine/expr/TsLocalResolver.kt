package org.usvm.machine.expr

import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.usvm.UExpr
import org.usvm.api.allocateConcreteRef
import org.usvm.dataflow.ts.infer.tryGetKnownType
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.getGlobals
import org.usvm.machine.interpreter.initializeGlobals
import org.usvm.machine.interpreter.isGlobalsInitialized
import org.usvm.machine.state.TsMethodResult
import org.usvm.memory.ULValue
import org.usvm.util.SymbolResolutionResult
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveImportInfo

private val logger = KotlinLogging.logger {}

fun TsContext.resolveLocal(
    scope: TsStepScope,
    local: EtsValue,
): UExpr<*>? {
    check(local is EtsLocal || local is EtsThis || local is EtsParameterRef) {
        "Expected EtsLocal, EtsThis, or EtsParameterRef, but got ${local::class.java}: $local"
    }

    // Handle closures
    if (local is EtsLocal && local.name.startsWith("%closures")) {
        // TODO: add comments
        val existingClosures = scope.calcOnState { closureObject[local.name] }
        if (existingClosures != null) {
            return existingClosures
        }
        val type = local.type
        check(type is EtsLexicalEnvType)
        val obj = allocateConcreteRef()
        // TODO: consider 'types.allocate'
        for (captured in type.closures) {
            val resolvedCaptured = resolveLocal(scope, captured) ?: return null
            val lValue = mkFieldLValue(resolvedCaptured.sort, obj, captured.name)
            scope.doWithState {
                memory.write(lValue, resolvedCaptured.cast(), guard = ctx.trueExpr)
            }
        }
        scope.doWithState {
            setClosureObject(local.name, obj)
        }
        return obj
    }

    val lValue = resolveLocalToLValue(scope, local) ?: return null
    return scope.calcOnState { memory.read(lValue) }
}

fun TsContext.resolveLocalToLValue(
    scope: TsStepScope,
    local: EtsValue,
): ULValue<*, *>? {
    val currentMethod = scope.calcOnState { lastEnteredMethod }

    if (currentMethod.name == DEFAULT_ARK_METHOD_NAME) {
        // TODO
    }

    // Get local index
    val idx = getLocalIdx(local, currentMethod)

    // If local is found in the current method:
    if (idx != null) {
        val sort = scope.calcOnState {
            getOrPutSortForLocal(idx) {
                val type = local.tryGetKnownType(currentMethod)
                typeToSort(type).let {
                    if (it is TsUnresolvedSort) {
                        addressSort
                    } else {
                        it
                    }
                }
            }
        }
        return mkRegisterStackLValue(sort, idx)
    }

    // Local not found, either global or imported
    val file = currentMethod.enclosingClass!!.declaringFile!!
    val globals = file.getGlobals()

    require(local is EtsLocal) {
        "Only locals are supported here, but got ${local::class.java}: $local"
    }

    // If local is a global variable:
    if (globals.any { it.name == local.name }) {
        val dfltObject = scope.calcOnState { getDfltObject(file) }

        // Initialize globals in `file` if necessary
        val isGlobalsInitialized = scope.calcOnState { isGlobalsInitialized(file) }
        if (!isGlobalsInitialized) {
            logger.info { "Globals are not initialized for file: $file" }
            scope.doWithState {
                initializeGlobals(file)
            }
            return null
        } else {
            // TODO: handle methodResult
            scope.doWithState {
                if (methodResult is TsMethodResult.Success) {
                    methodResult = TsMethodResult.NoCall
                }
            }
        }

        // Try to get the saved sort for this dflt object field
        val savedSort = scope.calcOnState {
            getSortForDfltObjectField(file, local.name)
        }

        if (savedSort == null) {
            // No saved sort means this field was never assigned to, which is an error
            logger.error { "Trying to read unassigned global variable: '$local' in $file" }
            scope.assert(falseExpr)
            return null
        }

        // Use the saved sort to read the field
        return mkFieldLValue(savedSort, dfltObject, local.name)
    }

    // If local is an imported variable:
    val importInfo = file.importInfos.find { it.name == local.name }
    if (importInfo != null) {
        return when (val resolutionResult = scene.resolveImportInfo(file, importInfo)) {
            is SymbolResolutionResult.Success -> {
                val importedFile = resolutionResult.file
                val importedDfltObject = scope.calcOnState { getDfltObject(importedFile) }

                // Initialize globals in the imported file if necessary
                val isImportedFileGlobalsInitialized = scope.calcOnState { isGlobalsInitialized(importedFile) }
                if (!isImportedFileGlobalsInitialized) {
                    logger.info { "Globals are not initialized for imported file: $importedFile" }
                    scope.doWithState {
                        initializeGlobals(importedFile)
                    }
                    return null
                }

                // Try to get the saved sort for this imported dflt object field
                val symbolNameInImportedFile = resolutionResult.exportInfo.originalName
                val savedSort = scope.calcOnState {
                    getSortForDfltObjectField(importedFile, symbolNameInImportedFile)
                }

                if (savedSort == null) {
                    // No saved sort means this field was never assigned to, which is an error
                    logger.error { "Trying to read unassigned imported symbol: '$local' from '$importedFile'" }
                    scope.assert(falseExpr)
                    return null
                }

                mkFieldLValue(savedSort, importedDfltObject, symbolNameInImportedFile)
            }

            is SymbolResolutionResult.FileNotFound -> {
                logger.error { "Cannot resolve import for '$local': ${resolutionResult.reason}" }
                scope.assert(falseExpr)
                return null
            }

            is SymbolResolutionResult.SymbolNotFound -> {
                logger.error { "Cannot find symbol '$local' in '${resolutionResult.file.name}': ${resolutionResult.reason}" }
                scope.assert(falseExpr)
                return null
            }
        }
    }

    logger.error { "Cannot resolve local variable '$local' in method: $currentMethod" }
    scope.assert(falseExpr)
    return null
}
