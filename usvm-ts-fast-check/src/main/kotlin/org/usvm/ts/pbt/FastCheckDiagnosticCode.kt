package org.usvm.ts.pbt

/** Stable identifiers for diagnostics created by the FastCheck integration. */
internal object FastCheckDiagnosticCode {
    const val CLI_ARGUMENT_INVALID = "cli.argument.invalid"
    const val CLI_COVERAGE_REQUIRED = "cli.coverage.required"
    const val CLI_COVERAGE_SCOPE_INVALID = "cli.coverage.scope.invalid"
    const val CLI_EXAMPLES_INVALID = "cli.examples.invalid"
    const val CLI_NUM_RUNS_INVALID = "cli.num-runs.invalid"
    const val CLI_PROPERTY_EMPTY = "cli.property.empty"
    const val CLI_PROPERTY_INVALID = "cli.property.invalid"
    const val CLI_PROPERTY_UNKNOWN = "cli.property.unknown"
    const val CLI_REGISTRY_EMPTY = "cli.registry.empty"
    const val CLI_REGISTRY_ID_DUPLICATE = "cli.registry.id.duplicate"
    const val CLI_REGISTRY_ID_INVALID = "cli.registry.id.invalid"
    const val CLI_REGISTRY_UNKNOWN = "cli.registry.unknown"
    const val CLI_SINGLE_PROPERTY_REQUIRED = "cli.single-property.required"
    const val CLI_SOURCE_ROOT_REQUIRED = "cli.source-root.required"
    const val CLI_TIMEOUT_INVALID = "cli.timeout.invalid"

    const val REGISTRY_PROPERTY_ID_DUPLICATE = "registry.property-id.duplicate"
    const val REGISTRY_PROPERTY_INVALID = "registry.property.invalid"
    const val REGISTRY_PROVIDER_LOAD_FAILED = "registry.provider.load.failed"

    const val BACKEND_EXAMPLES_ARITY = "backend.examples.arity"
    const val BACKEND_EXAMPLES_DOMAIN = "backend.examples.domain"
    const val BACKEND_EXAMPLES_VALUE_INVALID = "backend.examples.value.invalid"
    const val BACKEND_PROCESS_FAILED = "backend.process.failed"
    const val BACKEND_PROCESS_INTERRUPTED = "backend.process.interrupted"
    const val BACKEND_PROCESS_READ_FAILED = "backend.process.read.failed"
    const val BACKEND_PROCESS_START_FAILED = "backend.process.start.failed"
    const val BACKEND_PROCESS_TIMEOUT = "backend.process.timeout"
    const val BACKEND_PROCESS_WRITE_FAILED = "backend.process.write.failed"
    const val BACKEND_REQUEST_TOO_LARGE = "backend.request.too-large"
    const val BACKEND_RESPONSE_EMPTY = "backend.response.empty"
    const val BACKEND_RESPONSE_INVALID = "backend.response.invalid"
    const val BACKEND_RESPONSE_TOO_LARGE = "backend.response.too-large"
    const val BACKEND_RUNTIME_NOT_FOUND = "backend.runtime.not-found"

    const val COVERAGE_COLLECTOR_NOT_FOUND = "coverage.collector.not-found"
    const val COVERAGE_RUNTIME_UNSUPPORTED = "coverage.runtime.unsupported"
    const val COVERAGE_RUNTIME_VERSION_UNAVAILABLE = "coverage.runtime.version-unavailable"

    const val PROTOCOL_REQUEST_INVALID = "protocol.request.invalid"
    const val SOURCE_ROOT_INVALID = "source-root.invalid"
}
