export type AdapterDiagnosticKind = 'invalid-request' | 'entry-point';

export interface AdapterDiagnosticDescriptor {
  readonly kind: AdapterDiagnosticKind;
  readonly code: string;
}

const invalidRequest = (code: string): AdapterDiagnosticDescriptor => ({
  kind: 'invalid-request',
  code,
});

const entryPoint = (code: string): AdapterDiagnosticDescriptor => ({
  kind: 'entry-point',
  code,
});

/** Stable diagnostics emitted by the Node adapter and consumed by Kotlin. */
export const adapterDiagnostic = {
  protocolJsonInvalid: invalidRequest('protocol.json.invalid'),
  protocolRequestInvalid: invalidRequest('protocol.request.invalid'),
  protocolSeedInvalid: invalidRequest('protocol.seed.invalid'),
  protocolReplayPathInvalid: invalidRequest('protocol.replay-path.invalid'),
  protocolExamplesInvalid: invalidRequest('protocol.examples.invalid'),
  protocolExamplesArity: invalidRequest('protocol.examples.arity'),
  protocolManifestInvalid: invalidRequest('protocol.manifest.invalid'),
  protocolManifestInputInvalid: invalidRequest('protocol.manifest.input.invalid'),
  protocolEntryPointInvalid: invalidRequest('protocol.entrypoint.invalid'),
  sourceRootInvalid: invalidRequest('source-root.invalid'),
  domainInvalid: invalidRequest('domain.invalid'),
  domainKindUnknown: invalidRequest('domain.kind.unknown'),
  domainOptionalNil: invalidRequest('domain.optional.nil'),
  domainTupleEmpty: invalidRequest('domain.tuple.empty'),
  domainNumberAllowNaNInvalid: invalidRequest('domain.number.allow-nan.invalid'),
  domainNumberBoundNaN: invalidRequest('domain.number.bound.nan'),
  domainNumberBounds: invalidRequest('domain.number.bounds'),
  domainNumberNaNBounded: invalidRequest('domain.number.nan-bounded'),
  domainNumberEmpty: invalidRequest('domain.number.empty'),
  domainIntegerBounds: invalidRequest('domain.integer.bounds'),
  domainLengthInvalid: invalidRequest('domain.length.invalid'),
  jsValueInvalid: invalidRequest('js-value.invalid'),
  jsValueBooleanInvalid: invalidRequest('js-value.boolean.invalid'),
  jsValueStringInvalid: invalidRequest('js-value.string.invalid'),
  jsValueArrayInvalid: invalidRequest('js-value.array.invalid'),
  jsValueKindUnknown: invalidRequest('js-value.kind.unknown'),
  jsValueTypeUnsupported: invalidRequest('js-value.type.unsupported'),
  jsNumberInvalid: invalidRequest('js-number.invalid'),
  jsNumberEncodingInvalid: invalidRequest('js-number.encoding.invalid'),
  jsNumberKindUnknown: invalidRequest('js-number.kind.unknown'),
  entryPointExportNotFound: entryPoint('entrypoint.export.not-found'),
  entryPointExportNotFunction: entryPoint('entrypoint.export.not-function'),
  entryPointModuleOutsideRoot: entryPoint('entrypoint.module.outside-root'),
  entryPointModuleNotFound: entryPoint('entrypoint.module.not-found'),
  entryPointModuleAmbiguous: entryPoint('entrypoint.module.ambiguous'),
  entryPointModuleImportFailed: entryPoint('entrypoint.module.import-failed'),
  entryPointExecutionKindMismatch: entryPoint('entrypoint.execution-kind.mismatch'),
  entryPointResultInvalid: entryPoint('entrypoint.result.invalid'),
} as const;
