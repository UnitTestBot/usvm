package org.usvm.machine.state

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TrackedObjectProperty
import org.usvm.machine.interpreter.getDfltClass
import org.usvm.machine.interpreter.isGlobalsInitialized
import org.usvm.machine.interpreter.isInitializing
import org.usvm.memory.UReadOnlyMemory
import org.usvm.util.mkFieldLValue

/** Persistent heap and property metadata retained in RAM after a source initializer completes. */
internal data class TsInitialEnvironmentHeap(
    val memory: UReadOnlyMemory<EtsType>,
    val trackedProperties: Set<TrackedObjectProperty>,
    val writtenFields: Set<Pair<UHeapRef, String>>,
    val writtenFieldSorts: UPersistentHashMap<Pair<UHeapRef, String>, USort>,
    val typedArrayOverrides: Set<UHeapRef>,
    val moduleSorts: Map<Pair<EtsFileSignature, String>, USort>,
    val knownArrayPresence: Set<UHeapRef>,
    val initializedArrayIndices: Map<UHeapRef, Map<UExpr<TsSizeSort>, UBoolExpr>>,
)

/** Original storage binding, resolved with the same object-identity cache as entry inputs. */
internal data class TsInitialEnvironmentSlot(
    val owner: EtsClass,
    val field: EtsField,
    val value: UExpr<*>,
    val heap: TsInitialEnvironmentHeap,
    val storage: UFieldLValue<String, *>,
    val moduleBinding: Boolean,
)

internal fun TsState.captureInitialEnvironment(owner: EtsClass) = trackInitialEnvironment {
    captureEnvironment(owner)
}

private fun TsState.captureEnvironment(owner: EtsClass) {
    if (owner in initialEnvironmentHeaps) return
    val projectOwner = ctx.scene.projectClasses.any { it.signature == owner.signature }
    if (!projectOwner || ctx.isSourceModelClass(owner.signature)) return

    if (initialEnvironmentSlots.isNotEmpty()) {
        initialEnvironmentFailure =
            "A source initializer ran after entry environment data was accessed: ${owner.signature}"
    }

    getStaticInstance(owner)

    val initialMemory = captureInitialEnvironmentMemory()
    val moduleSorts = dfltObjectFieldSorts.associate { it.key to it.value }
    val heap = TsInitialEnvironmentHeap(
        memory = initialMemory,
        trackedProperties = trackedObjectProperties,
        writtenFields = writtenConcreteFields,
        writtenFieldSorts = writtenObjectLiteralFieldSorts,
        typedArrayOverrides = initializedTypedArrayOverrides,
        moduleSorts = moduleSorts,
        knownArrayPresence = knownArrayPropertyPresence,
        initializedArrayIndices = initializedArrayIndices,
    )
    // Before user code observes data, later natural initializers may mutate earlier owners.
    // Keep one coherent heap for all of them rather than one independently timed view per owner.
    initialEnvironmentHeaps = initialEnvironmentHeaps.mapValues { heap } + (owner to heap)
}

internal fun TsState.recordInitialStaticBinding(owner: EtsClass, signature: EtsFieldSignature, isWrite: Boolean) =
    trackInitialEnvironment { recordStaticBinding(owner, signature, isWrite = isWrite) }

private fun TsState.recordStaticBinding(owner: EtsClass, signature: EtsFieldSignature, isWrite: Boolean) {
    if (isInsideSourceInitializer()) return
    val heap = initialEnvironmentHeaps[owner] ?: return
    val field = owner.fields.singleOrNull { it.name == signature.name } ?: EtsFieldImpl(signature)
    if (!isWrite && field.type is EtsFunctionType) return

    val declaredSort = ctx.typeToSort(field.type)
    val storageSort = if (declaredSort == ctx.unresolvedSort) ctx.addressSort else declaredSort
    val storage = mkFieldLValue(storageSort, getStaticInstance(owner), field.name)
    recordInitialBinding(owner, field, storage, heap, moduleBinding = false)
}

internal fun TsState.recordInitialModuleBinding(file: EtsFile, name: String, isWrite: Boolean) =
    trackInitialEnvironment { recordModuleBinding(file, name, isWrite = isWrite) }

private fun TsState.recordModuleBinding(file: EtsFile, name: String, isWrite: Boolean) {
    val owner = file.getDfltClass()
    if (isInsideSourceInitializer()) return
    val heap = initialEnvironmentHeaps[owner] ?: return
    val initialSort = heap.moduleSorts[file.signature to name] ?: return
    val field = owner.fields.singleOrNull { it.name == name }
        ?: EtsFieldImpl(EtsFieldSignature(owner.signature, name, EtsUnknownType))
    if (!isWrite && field.type is EtsFunctionType) return

    val storage = mkFieldLValue(initialSort, getDfltObject(file), name)
    recordInitialBinding(owner, field, storage, heap, moduleBinding = true)
}

private fun TsState.recordInitialBinding(
    owner: EtsClass,
    field: EtsField,
    storage: UFieldLValue<String, *>,
    heap: TsInitialEnvironmentHeap,
    moduleBinding: Boolean,
) {
    val key = owner to field.name
    if (key in initialEnvironmentSlots) return

    val slot = TsInitialEnvironmentSlot(
        owner = owner,
        field = field,
        value = heap.memory.read(storage),
        heap = heap,
        storage = storage,
        moduleBinding = moduleBinding,
    )
    initialEnvironmentSlots = initialEnvironmentSlots + (key to slot)
}

private fun TsState.isInsideSourceInitializer(): Boolean = callStack.any { frame ->
    val method = frame.method
    val moduleInitializer = method.enclosingClass?.name == DEFAULT_ARK_CLASS_NAME &&
        method.name == DEFAULT_ARK_METHOD_NAME
    method.name == STATIC_INIT_METHOD_NAME || moduleInitializer
}

/** Initializers may remain on an unsupported prefix after the interpreter clears its call stack. */
internal fun TsState.hasPendingInitialEnvironment(): Boolean {
    val sourceOwners = ctx.scene.projectClasses.filterNot { ctx.isSourceModelClass(it.signature) }
    val pendingStatic = sourceOwners.any { owner -> staticStorage[owner] != null && isInitializing(owner) }
    if (pendingStatic) return true

    return ctx.scene.projectFiles.any { file ->
        val owner = file.classes.singleOrNull { it.name == DEFAULT_ARK_CLASS_NAME }
        if (owner == null) return@any false

        val sourceModule = !ctx.isSourceModelClass(owner.signature)
        val hasModuleStorage = dfltObject[file.signature] != null
        sourceModule && hasModuleStorage && owner !in initialEnvironmentHeaps && isGlobalsInitialized(file)
    }
}

/** Optional witness capture must never turn a reached program path into an interpreter failure. */
@Suppress("TooGenericExceptionCaught")
internal inline fun TsState.trackInitialEnvironment(action: TsState.() -> Unit) {
    if (initialEnvironmentFailure != null) return

    try {
        action()
    } catch (failure: NotImplementedError) {
        initialEnvironmentFailure = "Initial environment capture unsupported: ${failure.message}"
    } catch (failure: Exception) {
        initialEnvironmentFailure =
            "Initial environment capture failed: ${failure.javaClass.simpleName}: ${failure.message}"
    }
}
