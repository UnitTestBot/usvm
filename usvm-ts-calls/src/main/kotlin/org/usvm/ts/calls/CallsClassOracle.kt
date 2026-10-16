package org.usvm.ts.calls

import io.ksmt.utils.asExpr
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.FastCheckRuntime
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.util.EtsHierarchy
import org.usvm.util.arrayStorageType
import org.usvm.util.componentType
import org.usvm.util.declaredInstanceProperty
import org.usvm.util.hasBoxedArrayElement
import org.usvm.util.knownArrayIndexPresence
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.resolveStringValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.time.TimeSource

/** Unrepresentable observations remain evidence, never a successful equivalence check. */
@Serializable
internal data class CallsClassObservation(
    val completion: CallsCoverageCompletion,
    val values: Map<String, JsConcreteValue> = emptyMap(),
    val representable: Boolean = true,
    val diagnostic: String? = null,
)

@Serializable
internal data class CallsClassReplayObservation(
    val inputs: List<JsConcreteValue>,
    val observation: CallsClassObservation,
    val coveredStatementKeys: Set<String> = emptySet(),
    val replayElapsedMillis: Long = 0,
)

@Serializable
internal data class CallsClassOracleResult(val accepted: Boolean, val diagnostic: String? = null)

internal fun compareCallsClassObservations(
    symbolic: CallsClassObservation?,
    original: CallsClassObservation,
    instrumented: CallsClassObservation,
): CallsClassOracleResult {
    if (symbolic == null) return CallsClassOracleResult(accepted = false, diagnostic = "Symbolic observation missing")
    val observations = listOf(symbolic, original, instrumented)
    val unrepresentable = observations.firstOrNull { !it.representable }
    if (unrepresentable != null) {
        return CallsClassOracleResult(
            accepted = false,
            diagnostic = unrepresentable.diagnostic ?: "Unrepresentable observation"
        )
    }
    if (original != instrumented) {
        return CallsClassOracleResult(
            accepted = false,
            diagnostic = "Instrumentation changed returned values or receiver state"
        )
    }
    if (symbolic != original) {
        return CallsClassOracleResult(
            accepted = false,
            diagnostic = "Symbolic and original returned values or receiver state disagree"
        )
    }

    return CallsClassOracleResult(accepted = true)
}

/** Uses the same first model as CallsSymbolicInputs.resolve; no solver query changes candidate selection. */
internal fun extractCallsClassObservation(
    state: TsState,
    method: EtsMethod,
    fields: Map<String, PropertyDomain>,
): CallsClassObservation {
    val methodResult = state.methodResult
    if (methodResult is TsMethodResult.TsException) {
        return CallsClassObservation(
            completion = CallsCoverageCompletion.THREW,
            representable = false,
            diagnostic = "Thrown class scenarios have no complete return/state snapshot; " +
                "exception type=${methodResult.type}, value=${methodResult.value}",
        )
    }

    val completion = CallsCoverageCompletion.RETURNED
    return runCatching {
        val result = methodResult as? TsMethodResult.Success.RegularCall
            ?: error("Class scenario did not produce a regular returned snapshot")
        require(result.method == method) { "Class scenario did not complete its wrapper" }
        val value = ClassObservationValueResolver(state, state.models.first()).resolve(
            domain = ObjectDomain(fields),
            type = method.returnType,
            expression = result.value,
        ) as JsConcreteValue.Object
        CallsClassObservation(completion = completion, values = value.fields)
    }.getOrElse { error ->
        CallsClassObservation(
            completion = completion,
            representable = false,
            diagnostic = error.message ?: error::class.java.name,
        )
    }
}

private class ClassObservationValueResolver(
    private val state: TsState,
    private val model: UModelBase<EtsType>,
) {
    private val hierarchy = EtsHierarchy(state.ctx.scene)

    fun resolve(domain: PropertyDomain, type: EtsType, expression: UExpr<out USort>): JsConcreteValue = with(
        state.ctx
    ) {
        val value = unbox(expression)
        when (domain) {
            BooleanDomain -> JsConcreteValue.Boolean(model.eval(value.asExpr(boolSort)).toConcreteBoolValue())
            is NumberDomain, is IntegerDomain -> JsConcreteValue.number(
                model.eval(value.asExpr(fp64Sort)).extractDouble()
            )
            is StringDomain -> JsConcreteValue.String(
                state.resolveStringValue(
                    string = value.asExpr(addressSort),
                    model = model,
                    maxLength = domain.maxLength
                ),
            )
            is ConstantDomain -> resolveConstant(domain, value)
            is OptionalDomain -> resolveOptional(domain, type, value)
            is ObjectDomain -> resolveObject(domain, type, value)
            is ArrayDomain -> resolveArray(domain, type, value)
            is TupleDomain -> error("Tuple class observations are unsupported")
        }
    }

    private fun unbox(value: UExpr<out USort>): UExpr<out USort> = with(state.ctx) {
        if (value.sort != addressSort) return@with value

        val reference = model.eval(value.asExpr(addressSort))
        if (!reference.isFakeObject()) return@with value

        val fakeType = reference.getFakeType(state.memory)
        when {
            model.eval(fakeType.boolTypeExpr).isTrue -> reference.extractBool(state.memory)
            model.eval(fakeType.fpTypeExpr).isTrue -> reference.extractFp(state.memory)
            model.eval(fakeType.refTypeExpr).isTrue -> reference.extractRef(state.memory)
            else -> error("Observed fake value has no selected runtime type")
        }
    }

    private fun resolveOptional(
        domain: OptionalDomain,
        type: EtsType,
        value: UExpr<out USort>,
    ): JsConcreteValue = with(state.ctx) {
        val nil = if (domain.nil == JsConcreteValue.Null) mkTsNullValue() else mkUndefinedValue()
        val isNil = value.sort == addressSort && model.eval(mkHeapRefEq(value.asExpr(addressSort), nil)).isTrue
        if (isNil) return@with domain.nil

        val nestedType = (type as? EtsUnionType)?.types?.firstOrNull { nested ->
            nested != org.jacodb.ets.model.EtsNullType && nested != org.jacodb.ets.model.EtsUndefinedType
        } ?: type
        resolve(domain.value, nestedType, value)
    }

    private fun resolveObject(
        domain: ObjectDomain,
        type: EtsType,
        value: UExpr<out USort>,
    ): JsConcreteValue = with(state.ctx) {
        val reference = value.asExpr(addressSort)
        val values = domain.fields.mapValues { (name, nested) ->
            val fieldType = declaredInstanceProperty(type = type, name = name, hierarchy = hierarchy)?.type
                ?: error("Observed field $name has no declared type in $type")
            val sort = typeToSort(fieldType)
            val storageSort = if (sort is TsUnresolvedSort) addressSort else sort
            val fieldValue = state.memory.read(mkFieldLValue(storageSort, reference, name))
            resolve(nested, fieldType, fieldValue)
        }
        JsConcreteValue.Object(values)
    }

    private fun resolveConstant(domain: ConstantDomain, value: UExpr<out USort>): JsConcreteValue = with(state.ctx) {
        val resolved = when (val expected = domain.value) {
            JsConcreteValue.Undefined, JsConcreteValue.Null -> {
                val nil = if (expected == JsConcreteValue.Null) mkTsNullValue() else mkUndefinedValue()
                require(value.sort == addressSort && model.eval(mkHeapRefEq(value.asExpr(addressSort), nil)).isTrue) {
                    "Observed constant differs from its declared value"
                }
                expected
            }
            is JsConcreteValue.Boolean -> {
                JsConcreteValue.Boolean(model.eval(value.asExpr(boolSort)).toConcreteBoolValue())
            }
            is JsConcreteValue.Number -> {
                JsConcreteValue.number(model.eval(value.asExpr(fp64Sort)).extractDouble())
            }
            is JsConcreteValue.String -> {
                JsConcreteValue.String(
                    state.resolveStringValue(
                        string = value.asExpr(addressSort),
                        model = model,
                        maxLength = expected.value.length,
                    ),
                )
            }
            else -> { error("Structured constant class observations are unsupported") }
        }
        require(resolved == domain.value) { "Observed constant differs from its declared value" }
        resolved
    }

    private fun resolveArray(domain: ArrayDomain, type: EtsType, value: UExpr<out USort>): JsConcreteValue = with(
        state.ctx
    ) {
        val declaredArrayType = type as? EtsArrayType ?: error("Observed array has no declared array type: $type")
        val reference = value.asExpr(addressSort)
        val arrayType = state.arrayStorageType(reference, declaredArrayType) as? EtsArrayType
            ?: error("Observed array has no supported array storage: $type")
        val length = model.eval(state.memory.read(mkArrayLengthLValue(reference, arrayType))).extractInt()
        require(length in 0..domain.maxLength) { "Observed array exceeds the frozen observation capacity" }
        val elementSort = typeToSort(arrayType.componentType)
        val storageSort = if (elementSort is TsUnresolvedSort) addressSort else elementSort
        val elements = (0 until length).map { index ->
            val symbolicIndex = mkBv(index)
            val present = state.knownArrayIndexPresence(reference, symbolicIndex)
            require(present == null || model.eval(present).isTrue) {
                "Sparse array slot $index cannot be represented in class observations"
            }

            val boxed = state.hasBoxedArrayElement(reference, symbolicIndex)
            require(!model.eval(boxed).isTrue) {
                "BOXED array slot $index cannot be represented in class observations"
            }

            val lValue = mkArrayIndexLValue(storageSort, reference, symbolicIndex, arrayType)
            resolve(domain.element, declaredArrayType.componentType, state.memory.read(lValue))
        }
        JsConcreteValue.Array(elements)
    }
}

/** Replays the exact same scenario in a fresh process against original or counter-instrumented source. */
internal class OriginalTypeScriptClassReplayer(
    sourceRoot: Path,
    private val function: CallsFunctionCase,
    private val observationFields: Map<String, PropertyDomain>,
    sourceOverride: String? = null,
    overrideSourceFile: String? = null,
    private val counterGlobalName: String? = null,
) : AutoCloseable {
    private val root = sourceRoot.toRealPath()
    private val workspace = Files.createTempDirectory("usvm-calls-class-replay-")
    private val overlay = workspace.resolve("overlay")
    private val observationPath = workspace.resolve("observation.json")
    private val wrapperName = ".usvm-class-replay-${UUID.randomUUID()}.mts"
    private val backend: FastCheckBackend

    init {
        val setup = runCatching {
            require(
                (sourceOverride == null) == (overrideSourceFile == null)
            ) { "Source override needs a relative target file" }
            val closure = checkedCallsSourceClosure(root = root, entry = root.resolve(function.sourceFile))
            copyCallsSourceClosure(root = root, destination = overlay, closure = closure)
            if (overrideSourceFile != null) {
                require(overrideSourceFile in closure.files) { "Replay override is outside the frozen runtime closure" }
                Files.writeString(overlay.resolve(overrideSourceFile), requireNotNull(sourceOverride))
            }
            val tsconfig = writeCallsFrozenTsconfig(root = overlay, resolution = closure.resolution)
            val replayEntry = overlay.resolve(function.sourceFile)
            Files.deleteIfExists(replayEntry)
            val harnessSource = Files.readString(root.resolve(function.sourceFile))
            Files.writeString(replayEntry, nativeCheckedClassHarness(harnessSource))
            Files.writeString(
                overlay.resolve(wrapperName),
                classReplayWrapper(
                    sourceFile = function.sourceFile,
                    exportName = function.entryPoint.exportName,
                    observationPath = observationPath,
                    dropSentinel = function.inputs.isEmpty(),
                    counterGlobalName = counterGlobalName,
                    fields = observationFields.keys,
                )
            )
            FastCheckBackend(sourceRoots = listOf(overlay), tsconfig = tsconfig)
        }
        backend = setup.getOrElse { error ->
            close()
            throw error
        }
    }

    fun replay(inputs: List<JsConcreteValue>, timeoutMillis: Long): CallsClassReplayObservation {
        require(inputs.size == function.inputs.size)
        val emptyInputs = function.inputs.isEmpty()
        val observation = execute(
            seed = 0,
            timeoutMillis = timeoutMillis,
            examples = listOf(if (emptyInputs) listOf(JsConcreteValue.Boolean(true)) else inputs),
        )
        require(observation.inputs == inputs) { "Class replay input transport changed" }
        return observation
    }

    /** Native-only sampling uses the declared domains; it does not consult any symbolic profile or candidate. */
    fun sample(seed: Long, timeoutMillis: Long): CallsClassReplayObservation = execute(
        seed = seed,
        timeoutMillis = timeoutMillis,
        examples = emptyList(),
    )

    private fun execute(
        seed: Long,
        timeoutMillis: Long,
        examples: List<List<JsConcreteValue>>,
    ): CallsClassReplayObservation {
        require(timeoutMillis > 0)
        require(seed in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Native PBT seed must fit a signed 32-bit integer"
        }
        val nativeSeed = seed.toInt()
        Files.deleteIfExists(observationPath)
        val started = TimeSource.Monotonic.markNow()
        val emptyInputs = function.inputs.isEmpty()
        val propertyInputs = if (emptyInputs) {
            listOf(PropertyInput(name = "sentinel", domain = BooleanDomain))
        } else {
            function.inputs
        }
        val result = backend.run(
            property = PropertyDefinition(
                id = PropertyId("calls.class.exact-replay"),
                inputs = propertyInputs,
                predicate = TypeScriptEntryPoint(module = wrapperName, exportName = "replayClassScenario"),
            ),
            configuration = PropertyRunConfiguration(
                seed = nativeSeed,
                numRuns = 1,
                timeoutMillis = timeoutMillis,
                examples = examples,
            ),
        )
        require(result.status == PropertyRunStatus.SUCCESS && result.numRuns == 1) {
            "Class replay did not complete exactly once: $result"
        }
        val observation = CallsExperimentJson.json.decodeFromString<CallsClassReplayObservation>(
            Files.readString(observationPath)
        )
        val inputsInsideDomains = observation.inputs.size == function.inputs.size &&
            observation.inputs.zip(function.inputs).all { (value, input) -> value in input.domain }
        require(inputsInsideDomains) { "Native sample is outside the frozen constructor input domains" }
        val expectedFields = observationFields.keys
        val hasExactFields = !observation.observation.representable ||
            observation.observation.values.keys == expectedFields
        require(hasExactFields) { "Class replay omitted or added observation fields" }
        return observation.copy(replayElapsedMillis = started.elapsedNow().inWholeMilliseconds)
    }

    override fun close() {
        Files.walk(workspace).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}

private fun classReplayWrapper(
    sourceFile: String,
    exportName: String,
    observationPath: Path,
    dropSentinel: Boolean,
    counterGlobalName: String?,
    fields: Set<String>,
): String {
    val json = CallsExperimentJson.json
    val protocolModule = json.encodeToUtf8SafeString(FastCheckRuntime.protocolJsonModule().toUri().toString())
    val sourceModule = json.encodeToUtf8SafeString("./$sourceFile")
    val exportKey = json.encodeToUtf8SafeString(exportName)
    val outputPath = json.encodeToUtf8SafeString(observationPath.toString())
    val expectedFields = json.encodeToUtf8SafeString(fields.sorted())
    val actualArgs = if (dropSentinel) "args.slice(1)" else "args"
    val counterSetup = counterGlobalName?.let { name ->
        "(globalThis as any)[${json.encodeToUtf8SafeString(
            name
        )}] = new Set<string>();"
    }.orEmpty()
    val counterRead = counterGlobalName?.let { name ->
        "(globalThis as any)[${json.encodeToUtf8SafeString(name)}]"
    } ?: "[]"
    return """
        import { writeFileSync } from 'node:fs';
        import { stringifyProtocolJson } from $protocolModule;
        import * as scenarioModule from $sourceModule;

${classReplayValueEncoderSource().prependIndent(indent = "        ")}

${classReplayThrownValueDiagnosticSource().prependIndent(indent = "        ")}

        export function replayClassScenario(...args: unknown[]): boolean {
          const actualArgs = $actualArgs;
          const inputs = actualArgs.map(value => encode(value));
          $counterSetup
          let observation: any;
          let returned: any;
          let completion = 'RETURNED';
          try {
            returned = (scenarioModule as any)[$exportKey](...actualArgs);
          } catch (error) {
            completion = 'THREW';
            const isShapeFailure = error instanceof Error && error.message.startsWith('USVM class observation shape:');
            const diagnostic = isShapeFailure ? error.message :
              'Thrown class scenarios have no complete return/state snapshot; ' + describeThrownValue(error);
            observation = { completion, representable: false, diagnostic };
          }
          if (completion === 'RETURNED') {
            try {
              const encoded = encode(returned) as any;
              const exactFields = encoded.kind === 'object' &&
                JSON.stringify(Object.keys(encoded.fields).sort()) === JSON.stringify($expectedFields);
              if (!exactFields) {
                throw new Error('Class scenario observation fields differ from frozen surface');
              }
              observation = { completion, values: encoded.fields };
            } catch (error) {
              const diagnostic = error instanceof Error ? error.message : String(error);
              observation = { completion, representable: false, diagnostic };
            }
          }
          const coveredStatementKeys = Array.from($counterRead);
          writeFileSync($outputPath, stringifyProtocolJson({ inputs, observation, coveredStatementKeys }), 'utf8');
          return true;
        }
    """.trimIndent() + "\n"
}

private fun classReplayValueEncoderSource(): String = """
        function encode(value: unknown, seen = new Set<object>()): unknown {
          if (value === undefined) return { kind: 'undefined' };
          if (value === null) return { kind: 'null' };
          if (typeof value === 'boolean') return { kind: 'boolean', value };
          if (typeof value === 'string') return { kind: 'string', value };
          if (typeof value === 'number') {
            if (Number.isNaN(value)) return { kind: 'number', value: 'nan' };
            if (value === Infinity) return { kind: 'number', value: 'positive-infinity' };
            if (value === -Infinity) return { kind: 'number', value: 'negative-infinity' };
            const view = new DataView(new ArrayBuffer(8));
            view.setFloat64(0, value, false);
            const bits = view.getBigUint64(0, false).toString(16).padStart(16, '0');
            return { kind: 'number', value: 'finite', bits };
          }
          if (typeof value !== 'object') throw new Error('Unrepresentable observed value');
          if (seen.has(value)) throw new Error('Cyclic observed value');
          seen.add(value);
          let encoded: unknown;
          if (Array.isArray(value)) {
            for (let i = 0; i < value.length; i++) {
              if (!Object.hasOwn(value, i)) throw new Error('Sparse observed array');
            }
            encoded = { kind: 'array', elements: value.map(element => encode(element, seen)) };
          } else {
            const prototype = Object.getPrototypeOf(value);
            if (prototype !== Object.prototype && prototype !== null) throw new Error('Non-plain observed object');
            const fields: Record<string, unknown> = {};
            for (const key of Reflect.ownKeys(value)) {
              if (typeof key !== 'string' || ['__proto__', 'constructor', 'prototype'].includes(key)) {
                throw new Error('Unsupported observed property');
              }
              const descriptor = Object.getOwnPropertyDescriptor(value, key)!;
              if (!('value' in descriptor)) throw new Error('Accessor observed property');
              fields[key] = encode(descriptor.value, seen);
            }
            encoded = { kind: 'object', fields };
          }
          seen.delete(value);
          return encoded;
        }
""".trimIndent()

private fun classReplayThrownValueDiagnosticSource(): String = """
    function describeThrownValue(error: unknown): string {
      if (error instanceof Error) {
        return 'exception name=' + stringifyProtocolJson(error.name) +
          ', message=' + stringifyProtocolJson(error.message);
      }
      const valueType = typeof error;
      if (valueType === 'bigint' || valueType === 'symbol') {
        return 'thrown value type=' + valueType + ', value=' + stringifyProtocolJson(String(error));
      }
      if ((valueType === 'object' && error !== null) || valueType === 'function') {
        return 'thrown value type=' + valueType;
      }
      return 'thrown value=' + stringifyProtocolJson(encode(error));
    }
""".trimIndent()


/** Check raw values before projection, so snapshots cannot silently hide an unlisted concrete property. */
private fun nativeCheckedClassHarness(source: String): String {
    val marker = "// __usvm_class_observe "
    val registryMarker = "// __usvm_class_registry "
    val checked = source.lines().joinToString(separator = "\n") { line ->
        val offset = line.indexOf(marker)
        val registryOffset = line.indexOf(registryMarker)
        if (registryOffset >= 0) {
            val raw = line.substring(registryOffset + registryMarker.length)
            val registry = CallsExperimentJson.json.decodeFromString<List<CallsClassDataClass>>(raw)
            val entries = registry.mapIndexed { index, dataClass ->
                val fields = CallsExperimentJson.json.encodeToString<PropertyDomain>(ObjectDomain(dataClass.fields))
                val name = CallsExperimentJson.json.encodeToUtf8SafeString(dataClass.exportName)
                "{ constructor: CallsDataClass$index, prototype: CallsDataClass$index.prototype, " +
                    "name: $name, length: CallsDataClass$index.length, domain: $fields }"
            }
            "const __usvmClassDataRegistry = [${entries.joinToString()}];"
        } else if (offset < 0) {
            line
        } else {
            val raw = line.substring(offset + marker.length)
            val check = CallsExperimentJson.json.decodeFromString<CallsClassRawShapeCheck>(raw)
            val domain = CallsExperimentJson.json.encodeToString(check.domain)
            val allowCallableFields = if (check.skipCallableProperties) {
                "(${check.expression}) === receiver"
            } else {
                "false"
            }
            val allowedClass = if (check.allowTargetClass) "CallsTargetClass" else "undefined"
            val callableNames = CallsExperimentJson.json.encodeToUtf8SafeString(check.callablePropertyNames.sorted())
            val registry = if (check.dataClasses.isEmpty()) "[]" else "__usvmClassDataRegistry"
            "  __usvmAssertObservedShape(${check.expression}, $domain, " +
                "$allowCallableFields, $allowedClass, $callableNames, $registry);"
        }
    }
    return "$NATIVE_CLASS_SHAPE_VALIDATOR\n$checked"
}

private val NATIVE_CLASS_SHAPE_VALIDATOR = """
        function __usvmCanonicalDomain(domain: any): any {
          if (domain === null || typeof domain !== 'object') return domain;
          if (Array.isArray(domain)) return domain.map(__usvmCanonicalDomain);
          return Object.fromEntries(Object.keys(domain).sort().map(key => [key, __usvmCanonicalDomain(domain[key])]));
        }
        function __usvmAssertObservedShape(
          value: any, domain: any, skipCallableProperties = false, allowedClass?: any,
          callablePropertyNames: string[] = [], dataClasses: any[] = [],
        ): void {
          const fail = (reason: string): never => { throw new Error('USVM class observation shape: ' + reason); };
          const exactOwnKeys = (value: any, names: string[]): boolean => {
            const keys = Reflect.ownKeys(value);
            return keys.every(key => typeof key === 'string') &&
              JSON.stringify(keys.sort()) === JSON.stringify([...names].sort());
          };
          for (const entry of dataClasses) {
            const ctor = entry.constructor;
            const prototype = entry.prototype;
            const validConstructor = Object.getPrototypeOf(ctor) === Function.prototype &&
              ctor.prototype === prototype && ctor.name === entry.name && ctor.length === entry.length &&
              exactOwnKeys(ctor, ['length', 'name', 'prototype']);
            const descriptor = Object.getOwnPropertyDescriptor(prototype, 'constructor');
            const validPrototype = Object.getPrototypeOf(prototype) === Object.prototype &&
              exactOwnKeys(prototype, ['constructor']) && descriptor && 'value' in descriptor &&
              descriptor.value === ctor && descriptor.writable && descriptor.configurable && !descriptor.enumerable;
            const validConstructorProperties = ['length', 'name', 'prototype'].every(name => {
              const property = Object.getOwnPropertyDescriptor(ctor, name)!;
              return property && 'value' in property && !property.enumerable && !property.writable &&
                property.configurable === (name !== 'prototype');
            });
            if (!validConstructor || !validPrototype || !validConstructorProperties) {
              fail('Registered data-class constructor or prototype changed');
            }
          }
          if (domain.kind === 'optional') {
            const nil = domain.nil?.kind === 'null' ? null : undefined;
            if (value !== nil) {
              __usvmAssertObservedShape(
                value, domain.value, skipCallableProperties, allowedClass, callablePropertyNames, dataClasses,
              );
            }
          } else if (domain.kind === 'object') {
            if (value === null || typeof value !== 'object' || Array.isArray(value)) fail('Expected object');
            const prototype = Object.getPrototypeOf(value);
            const knownClass = allowedClass !== undefined && value instanceof allowedClass &&
              prototype === allowedClass.prototype;
            const dataClass = dataClasses.find(entry =>
              prototype === entry.prototype && value instanceof entry.constructor);
            if (prototype !== Object.prototype && prototype !== null && !knownClass && !dataClass) {
              fail('Unlisted object prototype');
            }
            if (dataClass && JSON.stringify(__usvmCanonicalDomain(domain)) !==
                JSON.stringify(__usvmCanonicalDomain(dataClass.domain))) {
              fail('Registered data-class observation domain changed');
            }
            if (dataClass && !exactOwnKeys(value, Object.keys(dataClass.domain.fields))) {
              fail('Registered data-class own fields changed');
            }
            if (dataClass) {
              for (const [key, nested] of Object.entries(dataClass.domain.fields)) {
                const descriptor = Object.getOwnPropertyDescriptor(value, key)!;
                const ordinaryField = 'value' in descriptor && descriptor.enumerable &&
                  descriptor.writable && descriptor.configurable;
                if (!ordinaryField) fail('Registered data-class field is not an ordinary data property');
                __usvmAssertObservedShape(value[key], nested, false, undefined, [], dataClasses);
              }
            }
            const allowedCallables = new Set(skipCallableProperties ? callablePropertyNames : []);
            for (const name of allowedCallables) {
              const descriptor = Object.getOwnPropertyDescriptor(value, name);
              if (!descriptor || !('value' in descriptor) || typeof descriptor.value !== 'function') {
                fail('Declared callable property changed kind or disappeared');
              }
            }
            const keys = Reflect.ownKeys(value).filter(key => {
              const descriptor = Object.getOwnPropertyDescriptor(value, key)!;
              if (!('value' in descriptor)) fail('Accessor in observed data');
              return !(typeof key === 'string' && allowedCallables.has(key) && typeof descriptor.value === 'function');
            });
            const exactKeys = !keys.some(key => typeof key !== 'string') &&
              JSON.stringify(keys.sort()) === JSON.stringify(Object.keys(domain.fields).sort());
            if (!exactKeys) {
              fail('Unlisted or missing object property');
            }
            for (const [key, nested] of Object.entries(domain.fields)) {
              __usvmAssertObservedShape(value[key], nested, false, undefined, [], dataClasses);
            }
          } else if (domain.kind === 'array') {
            const validArray = Array.isArray(value) && value.length <= (domain.maxLength ?? 10);
            if (!validArray) fail('Array exceeds observation capacity');
            for (let i = 0; i < value.length; i++) {
              if (!Object.hasOwn(value, i)) fail('Sparse array');
              __usvmAssertObservedShape(value[i], domain.element, false, undefined, [], dataClasses);
            }
            const allowed = new Set(['length', ...value.map((_: any, index: number) => String(index))]);
            const unlistedProperty = Reflect.ownKeys(value).some(key => typeof key !== 'string' || !allowed.has(key));
            if (unlistedProperty) fail('Unlisted array property');
          } else if (domain.kind === 'string') {
            const validString = typeof value === 'string' && value.length <= (domain.maxLength ?? 10);
            if (!validString) fail('String exceeds observation capacity');
          } else if (domain.kind === 'number' || domain.kind === 'integer') {
            if (typeof value !== 'number') fail('Expected number');
          } else if (domain.kind === 'boolean') {
            if (typeof value !== 'boolean') fail('Expected boolean');
          } else if (domain.kind === 'constant') {
            const expected = domain.value;
            if (expected.kind === 'undefined' && value !== undefined) fail('Expected undefined');
            if (expected.kind === 'null' && value !== null) fail('Expected null');
            if (expected.kind === 'boolean' || expected.kind === 'string') {
              if (value !== expected.value) fail('Constant changed');
            }
            if (expected.kind === 'number' && typeof value !== 'number') fail('Expected constant number');
          } else fail('Unsupported observation domain');
        }
""".trimIndent()
