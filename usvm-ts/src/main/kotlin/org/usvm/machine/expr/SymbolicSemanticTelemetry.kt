package org.usvm.machine.expr

import mu.KotlinLogging

/**
 * Stable reason vocabulary for every remaining symbolic call approximation.
 *
 * The prefix is intentionally line-oriented so campaign log collectors can
 * ingest it without parsing the human-readable call signature.
 */
enum class SymbolicSemanticReason(val code: String) {
    LEGACY_ANY_RECEIVER("legacy_any_receiver_mock"),
    LEGACY_NO_SUITABLE_VIRTUAL_TARGET("legacy_no_suitable_virtual_target_mock"),
    LEGACY_METHOD_WITHOUT_ENTRY_POINT("legacy_method_without_entry_point_mock"),
    INTERPROCEDURAL_ANALYSIS_DISABLED("interprocedural_analysis_disabled_mock"),
    UNRESOLVED_POINTER_CALL("unresolved_pointer_call_mock"),
    UNRESOLVED_VIRTUAL_CLASS("unresolved_virtual_class_mock"),
    UNRESOLVED_VIRTUAL_METHOD("unresolved_virtual_method_mock"),
    CALLABLE_REFERENCE_NOT_MATERIALIZED("callable_reference_not_materialized"),
    DYNAMIC_CALLABLE_ALTERNATIVE_REJECTED("dynamic_callable_alternative_rejected"),
    MODULE_NAMESPACE_BINDING_NOT_MATERIALIZED("module_namespace_binding_not_materialized"),
    ITERATOR_PROTOCOL_OUTSIDE_EXACT_SUBSET("iterator_protocol_outside_exact_subset"),
    PROPERTY_PRESENCE_NOT_TRACKED("property_presence_not_tracked"),
    MAP_STATE_NOT_MATERIALIZED("map_state_not_materialized"),
    BUILTIN_OUTSIDE_EXACT_SUBSET("builtin_outside_exact_subset"),
}

private val semanticTelemetryLogger = KotlinLogging.logger("org.usvm.machine.symbolic.semantic.telemetry")

internal fun recordSymbolicSemanticFallback(
    reason: SymbolicSemanticReason,
    call: Any,
) {
    semanticTelemetryLogger.warn {
        "symbolic_semantic_fallback reason=${reason.code} call=${call.toString().replace('\n', ' ')}"
    }
}
