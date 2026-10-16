package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.usvm.api.TsParametersState
import org.usvm.api.TsTestValue
import org.usvm.ts.pbt.model.JsConcreteValue
import java.util.IdentityHashMap

/** A primitive transport value or a reference into one concrete test's object table. */
@Serializable
internal data class CallsConcreteHeapValue(
    val primitive: JsConcreteValue? = null,
    val objectId: Int? = null,
) {
    init {
        require((primitive == null) != (objectId == null))
        require(primitive !is JsConcreteValue.Array && primitive !is JsConcreteValue.Object)
    }
}

@Serializable
internal data class CallsConcreteHeapObject(
    val id: Int,
    val classSignature: String? = null,
    val classSourceFile: String? = null,
    val className: String? = null,
    val fields: Map<String, CallsConcreteHeapValue>? = null,
    val elements: List<CallsConcreteHeapValue>? = null,
) {
    init {
        require((fields == null) != (elements == null))
    }
}

@Serializable
internal enum class CallsConcreteEnvironmentFieldKind { STATIC_FIELD, MODULE_BINDING }

@Serializable
internal data class CallsConcreteEnvironmentField(
    val kind: CallsConcreteEnvironmentFieldKind,
    val ownerSignature: String,
    val sourceFile: String,
    val ownerName: String,
    val name: String,
    val value: CallsConcreteHeapValue,
)

/** Initial reachable input heap only; static/module environment completeness is reported separately. */
@Serializable
internal data class CallsConcreteHeap(
    val receiver: CallsConcreteHeapValue?,
    val arguments: List<CallsConcreteHeapValue>,
    val objects: List<CallsConcreteHeapObject>,
    val environmentFields: List<CallsConcreteEnvironmentField> = emptyList(),
)

internal data class CallsConcretePrototype(val signature: EtsClassSignature, val ordinaryObject: Boolean)

internal fun concreteCallsInputHeap(
    inputs: TsParametersState,
    prototypes: Map<TsTestValue.TsClass, CallsConcretePrototype>,
): CallsConcreteHeap {
    val encoder = ConcreteHeapEncoder(prototypes = prototypes)
    val receiver = inputs.thisInstance?.let { encoder.value(it) }
    val arguments = inputs.parameters.map { encoder.value(it) }
    val fields = inputs.globals.flatMap { (owner, fields) ->
        fields.map { field ->
            CallsConcreteEnvironmentField(
                kind = if (owner.name == DEFAULT_ARK_CLASS_NAME) {
                    CallsConcreteEnvironmentFieldKind.MODULE_BINDING
                } else {
                    CallsConcreteEnvironmentFieldKind.STATIC_FIELD
                },
                ownerSignature = owner.signature.toString(),
                sourceFile = owner.signature.file.fileName,
                ownerName = owner.name,
                name = field.field.name,
                value = encoder.value(field.value),
            )
        }
    }
    return CallsConcreteHeap(
        receiver = receiver,
        arguments = arguments,
        objects = encoder.objects(),
        environmentFields = fields,
    )
}

private class ConcreteHeapEncoder(private val prototypes: Map<TsTestValue.TsClass, CallsConcretePrototype>) {
    private val identities = IdentityHashMap<TsTestValue, Int>()
    private val objects = mutableListOf<CallsConcreteHeapObject?>()

    fun objects(): List<CallsConcreteHeapObject> = objects.map { checkNotNull(it) }

    fun value(value: TsTestValue): CallsConcreteHeapValue {
        val primitive = when (value) {
            TsTestValue.TsNull -> JsConcreteValue.Null
            TsTestValue.TsUndefined -> JsConcreteValue.Undefined
            is TsTestValue.TsBoolean -> JsConcreteValue.Boolean(value.value)
            is TsTestValue.TsString -> JsConcreteValue.String(value.value)
            is TsTestValue.TsNumber -> JsConcreteValue.number(value.number)
            is TsTestValue.TsClass, is TsTestValue.TsArray<*> -> null
            else -> error("Unsupported concrete environment value: ${value::class.java.simpleName}")
        }
        if (primitive != null) return CallsConcreteHeapValue(primitive = primitive)

        val previous = identities[value]
        if (previous != null) return CallsConcreteHeapValue(objectId = previous)

        val id = objects.size
        identities[value] = id
        objects += null
        objects[id] = when (value) {
            is TsTestValue.TsArray<*> -> CallsConcreteHeapObject(
                id = id,
                elements = value.values.map { value(it) },
            )
            is TsTestValue.TsClass -> objectRecord(id = id, value = value)
            else -> error("Expected an object environment value")
        }
        return CallsConcreteHeapValue(objectId = id)
    }

    private fun objectRecord(id: Int, value: TsTestValue.TsClass): CallsConcreteHeapObject {
        val fields = value.properties.mapValues { (_, field) -> value(field) }
        val prototype = checkNotNull(prototypes[value]) { "Concrete prototype identity is unavailable" }
        if (prototype.ordinaryObject) return CallsConcreteHeapObject(id = id, fields = fields)
        val signature = prototype.signature

        return CallsConcreteHeapObject(
            id = id,
            classSignature = signature.toString(),
            classSourceFile = signature.file.fileName,
            className = signature.name,
            fields = fields,
        )
    }
}
