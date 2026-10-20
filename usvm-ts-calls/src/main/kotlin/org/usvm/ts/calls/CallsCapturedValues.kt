package org.usvm.ts.calls

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.allocateConcreteRef
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkInitialFakeValue
import org.usvm.ts.pbt.fastcheck.TypeScriptCaptureKind
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceCapture
import org.usvm.ts.pbt.mapping.EtsLexicalEnvironmentBinding
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue

internal fun callsCapturePreflight(
    scene: EtsScene,
    binding: EtsLexicalEnvironmentBinding,
    captures: List<TypeScriptSourceCapture>,
): String? {
    val environment = binding.parameter.type as? EtsLexicalEnvType
        ?: return "Mapped capture parameter does not have a lexical-environment type"
    val valuesByName = captures.associateBy(TypeScriptSourceCapture::name)
    for (local in environment.closures) {
        val capture = valuesByName[local.name] ?: return "Missing source provenance for capture ${local.name}"
        val reason = capture.captureLimitation(scene, local.type)
        if (reason != null) return "Capture ${local.name}: $reason"
    }

    return null
}

internal fun TsState.initializeCallsCaptures(
    binding: EtsLexicalEnvironmentBinding,
    captures: List<TypeScriptSourceCapture>,
) = with(ctx) {
    val environment = binding.parameter.type as EtsLexicalEnvType
    val environmentRef = allocateConcreteRef()
    val valuesByName = captures.associateBy(TypeScriptSourceCapture::name)
    val objectReferences = mutableMapOf<String, UHeapRef>()
    for (local in environment.closures) {
        val payload = resolveCapturedValue(valuesByName.getValue(local.name), local.type, objectReferences)
        val stored = captureStorageValue(payload, local.type)
        memory.write(
            mkFieldLValue(stored.sort, environmentRef, local.name),
            stored.asExpr(stored.sort),
            guard = trueExpr,
        )
    }
    memory.write(
        mkRegisterStackLValue(addressSort, binding.stackSlot),
        environmentRef.asExpr(addressSort),
        guard = trueExpr,
    )
    saveSortForLocal(binding.stackSlot, addressSort)
}

private fun TypeScriptSourceCapture.captureLimitation(scene: EtsScene, type: EtsType): String? = when (kind) {
    TypeScriptCaptureKind.UNSUPPORTED -> {
        reason ?: "Source binding requires runtime initialization"
    }
    TypeScriptCaptureKind.BUILTIN -> {
        if (value in SUPPORTED_CAPTURE_OWNERS) null else "Global builtin $value has no supported Calls owner"
    }
    TypeScriptCaptureKind.FUNCTION -> {
        if (sourceFunction(scene, type) == null) "Source function has unsupported captures or no unique body" else null
    }
    TypeScriptCaptureKind.OBJECT -> {
        objectCaptureLimitation(scene, type)
    }
    else -> {
        null
    }
}

private fun TypeScriptSourceCapture.objectCaptureLimitation(scene: EtsScene, type: EtsType): String? {
    val objectType = type as? EtsClassType ?: return "Object capture lacks its source class identity"
    val owner = scene.projectClasses.singleOrNull { clazz -> clazz.signature == objectType.signature }
        ?: return "Object capture source class is not unique"
    for (field in fields) {
        val declaration = owner.fields.singleOrNull { candidate -> candidate.name == field.name }
            ?: return "Object capture field ${field.name} has no unique source declaration"
        val reason = field.captureLimitation(scene, declaration.type)
        if (reason != null) return reason
    }

    return null
}

private fun sourceFunction(scene: EtsScene, type: EtsType): EtsMethod? {
    val signature = (type as? EtsFunctionType)?.signature ?: return null
    return scene.projectClasses.asSequence()
        .filter { clazz -> clazz.signature == signature.enclosingClass }
        .flatMap { clazz -> clazz.methods.asSequence() }
        .filter { method -> method.signature == signature && method.cfg.stmts.isNotEmpty() }
        .filter { method -> method.parameters.firstOrNull()?.type !is EtsLexicalEnvType }
        .singleOrNull()
}

private fun TsState.resolveCapturedValue(
    capture: TypeScriptSourceCapture,
    type: EtsType,
    objectReferences: MutableMap<String, UHeapRef>,
): UExpr<*> = with(ctx) {
    when (capture.kind) {
        TypeScriptCaptureKind.NUMBER -> {
            when (val value = requireNotNull(capture.value)) {
                "NaN" -> mkFp64NaN()
                "Infinity" -> mkFpInf(signBit = false, sort = fp64Sort)
                "-Infinity" -> mkFpInf(signBit = true, sort = fp64Sort)
                else -> mkFp64(value.toDouble())
            }
        }
        TypeScriptCaptureKind.STRING -> {
            mkInitializedStringConstant(requireNotNull(capture.value))
        }
        TypeScriptCaptureKind.BOOLEAN -> {
            mkBool(requireNotNull(capture.value).toBooleanStrict())
        }
        TypeScriptCaptureKind.NULL -> {
            mkTsNullValue()
        }
        TypeScriptCaptureKind.UNDEFINED -> {
            mkUndefinedValue()
        }
        TypeScriptCaptureKind.BUILTIN -> {
            memory.allocConcrete(type)
        }
        TypeScriptCaptureKind.FUNCTION -> {
            val method = requireNotNull(sourceFunction(scene, type))
            getMethodRef(method = method)
        }
        TypeScriptCaptureKind.OBJECT -> {
            val objectType = type as EtsClassType
            val owner = scene.projectClasses.single { clazz -> clazz.signature == objectType.signature }
            val origin = requireNotNull(capture.origin)
            val existing = objectReferences[origin]
            if (existing != null) return existing

            val ref = memory.allocConcrete(objectType)
            objectReferences[origin] = ref
            for (field in capture.fields) {
                val fieldType = owner.fields.single { declaration -> declaration.name == field.name }.type
                val payload = resolveCapturedValue(field, fieldType, objectReferences)
                val stored = captureStorageValue(payload, fieldType)
                memory.write(mkFieldLValue(stored.sort, ref, field.name), stored.asExpr(stored.sort), guard = trueExpr)
                writtenConcreteFields += ref to field.name
            }
            ref
        }
        TypeScriptCaptureKind.UNSUPPORTED -> {
            error("Unsupported capture passed preflight: ${capture.name}")
        }
    }
}

private fun TsState.captureStorageValue(payload: UExpr<*>, type: EtsType): UExpr<*> = with(ctx) {
    val declaredSort = typeToSort(type)
    if (declaredSort !is TsUnresolvedSort) return payload

    when (payload.sort) {
        boolSort -> mkInitialFakeValue(boolValue = payload.asExpr(boolSort))
        fp64Sort -> mkInitialFakeValue(fpValue = payload.asExpr(fp64Sort))
        addressSort -> mkInitialFakeValue(refValue = payload.asExpr(addressSort))
        else -> error("Unsupported captured payload sort: ${payload.sort}")
    }
}

private val SUPPORTED_CAPTURE_OWNERS = setOf("Array", "Boolean", "Date", "Error", "Math", "Number", "Object", "String")
