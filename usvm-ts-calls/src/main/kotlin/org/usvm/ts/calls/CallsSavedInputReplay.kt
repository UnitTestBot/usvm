package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.usvm.ts.pbt.fastcheck.TypeScriptLocalSourceClosure
import org.usvm.ts.pbt.fastcheck.TypeScriptSavedInputBindings
import org.usvm.ts.pbt.fastcheck.TypeScriptSavedInputClass
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

@Serializable
internal enum class CallsSavedInputReplayStatus { INVOKED, UNSUPPORTED, HARNESS_FAILURE }

/** Invocation evidence only: neither the suspended source location nor native class coverage is attested. */
@Serializable
internal data class CallsSavedInputReplayResult(
    val stateId: String,
    val status: CallsSavedInputReplayStatus,
    val diagnostic: String? = null,
    val observation: CallsClassReplayObservation? = null,
    val sourceInitializationDiagnostics: List<String> = emptyList(),
    val nativeReplayValidated: Boolean = false,
    val nativeCoverageAvailable: Boolean = false,
)

/** Select one immutable journal record and verify both frozen inputs and its producing probe manifest. */
internal fun runSavedInputReplay(args: List<String>) {
    require(args.size == SAVED_INPUT_REPLAY_ARGUMENT_COUNT) {
        "class-replay-input needs <startup-manifest> <class-id> <probe-directory> <state-id> <timeout-ms> <output.json>"
    }
    val manifestPath = Path.of(args[0]).toRealPath()
    val manifestBytes = Files.readAllBytes(manifestPath)
    val manifest = CallsExperimentJson.json.decodeFromString<CallsClassStartManifest>(manifestBytes.toString(Charsets.UTF_8))
    val case = manifest.classes.single { it.classId == args[1] }
    val root = case.verifyInputs()
    val probe = Path.of(args[2]).toRealPath()
    val result = CallsExperimentJson.json.decodeFromString<CallsClassStartResult>(Files.readString(probe.resolve("result.json")))
    require(result.classId == case.classId && result.manifestSha256 == callsArtifactSha256(manifestBytes)) {
        "Saved input probe differs from the supplied class manifest"
    }
    val metadata = checkNotNull(result.rawAnalysis?.inputCandidates) { "Probe has no concrete input journal" }
    require(metadata.complete && metadata.schemaVersion == 1 && metadata.format == "JSONL") {
        "Saved input replay needs a complete supported concrete journal"
    }
    val journal = probe.resolve(metadata.path).normalize().toRealPath()
    require(journal.startsWith(probe)) { "Input journal escapes the probe directory" }
    require(Files.size(journal) == metadata.bytes && savedInputFileSha256(journal) == metadata.sha256) {
        "Saved concrete input journal changed"
    }
    val stateId = args[3]
    val candidate = Files.lines(journal).use { lines ->
        lines.map { CallsExperimentJson.json.decodeFromString<CallsClassInputCandidate>(it) }
            .filter { it.stateId == stateId }.toList().single()
    }
    require(candidate.className == case.className && candidate.profile == manifest.profile) {
        "Saved candidate differs from the manifest class or profile"
    }
    val replay = replayCallsSavedInput(
        sourceRoot = root, sourceFile = case.sourceFile, targetSignature = checkNotNull(result.targetSignature),
        candidate = candidate, timeoutMillis = args[4].toLong(),
    )
    case.verifyInputs()

    val output = Path.of(args[5]).toAbsolutePath().normalize()
    output.parent?.let(Files::createDirectories)
    Files.writeString(output, encodeCallsArtifact(replay) + "\n", StandardOpenOption.CREATE_NEW)
}

/** Reuses the normal frozen-closure copier, supervised native process and class observation transport. */
internal fun replayCallsSavedInput(
    sourceRoot: Path,
    sourceFile: String,
    targetSignature: String,
    candidate: CallsClassInputCandidate,
    timeoutMillis: Long,
): CallsSavedInputReplayResult {
    require(timeoutMillis > 0)
    if (!candidate.environmentComplete) {
        return unsupportedSavedInput(
            candidate = candidate, reason = "Incomplete initial environment: ${candidate.environmentLimitations}",
        )
    }
    if (candidate.methodName.startsWith('%')) {
        return unsupportedSavedInput(
            candidate = candidate, reason = "Synthetic initializer or lowered entry point is not a native callable",
        )
    }

    val root = sourceRoot.toRealPath()
    val closure = savedInputSourceClosure(root = root, sourceFile = sourceFile)
    if (closure.reasonCode != null) {
        return unsupportedSavedInput(candidate = candidate, reason = "${closure.reasonCode}: ${closure.diagnostic}")
    }
    val hasUnresolvedRuntimeBoundary = closure.resolution?.runtimeBoundaries.orEmpty().isNotEmpty()
    if (hasUnresolvedRuntimeBoundary) {
        return unsupportedSavedInput(candidate = candidate, reason = "Runtime source closure has unresolved module boundaries")
    }
    val plan = try {
        savedInputBindingPlan(
            sourceFile = sourceFile, targetSignature = targetSignature, candidate = candidate,
            closureFiles = closure.files.toSet(),
        )
    } catch (unsupported: UnsupportedSavedInputBinding) {
        return unsupportedSavedInput(candidate = candidate, reason = checkNotNull(unsupported.message))
    }

    val workspace = Files.createTempDirectory("usvm-calls-saved-input-")
    try {
        copyCallsSourceClosure(root = root, destination = workspace, closure = closure)
        writeCallsFrozenTsconfig(root = workspace, resolution = closure.resolution)
        for ((file, bindings) in plan.modules) {
            val prepared = TypeScriptSourceInspector.savedInputBindings(source = root.resolve(file), bindings = bindings)
            val preparedSource = prepared.source
            if (preparedSource == null) {
                return unsupportedSavedInput(candidate = candidate, reason = checkNotNull(prepared.reason))
            }

            Files.writeString(workspace.resolve(file), preparedSource)
        }
        val harnessName = ".usvm-saved-input-${UUID.randomUUID()}.mts"
        Files.writeString(workspace.resolve(harnessName), savedInputHarness(candidate = candidate, plan = plan))
        val function = CallsFunctionCase(
            functionId = "calls.saved-input.${candidate.stateId}",
            sourceFile = harnessName,
            entryPoint = TypeScriptEntryPoint(module = harnessName, exportName = "invokeSavedInput"),
            inputs = emptyList(),
            targets = emptyList(),
        )
        val fields = mapOf(
            "completion" to StringDomain(), "returnValue" to StringDomain(maxLength = Int.MAX_VALUE),
            "returnValueRepresentable" to BooleanDomain, "diagnostic" to StringDomain(maxLength = Int.MAX_VALUE),
        )
        val preparedClosure = savedInputSourceClosure(root = workspace, sourceFile = harnessName)
        val preparedClosureResolved = preparedClosure.reasonCode == null &&
            preparedClosure.resolution?.runtimeBoundaries.orEmpty().isEmpty()
        require(preparedClosureResolved) {
            "Generated saved input harness has an unresolved native runtime closure"
        }
        val observation = OriginalTypeScriptClassReplayer(
            sourceRoot = workspace, function = function, observationFields = fields,
            sourceClosure = preparedClosure,
        ).use { replayer -> replayer.replay(inputs = emptyList(), timeoutMillis = timeoutMillis) }
        val invoked = observation.observation.completion == CallsCoverageCompletion.RETURNED &&
            observation.observation.representable

        return CallsSavedInputReplayResult(
            stateId = candidate.stateId,
            status = if (invoked) CallsSavedInputReplayStatus.INVOKED else CallsSavedInputReplayStatus.HARNESS_FAILURE,
            diagnostic = if (invoked) null else observation.observation.diagnostic,
            observation = observation,
            sourceInitializationDiagnostics = closure.initializationIssues.map { it.diagnostic },
        )
    } finally {
        Files.walk(workspace).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}

private fun savedInputSourceClosure(root: Path, sourceFile: String): TypeScriptLocalSourceClosure =
    TypeScriptSourceInspector.startupSourceClosure(sourceRoot = root, source = root.resolve(sourceFile))

private fun savedInputFileSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(Files.newInputStream(path), digest).use { input -> input.transferTo(OutputStream.nullOutputStream()) }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}

private fun unsupportedSavedInput(candidate: CallsClassInputCandidate, reason: String): CallsSavedInputReplayResult =
    CallsSavedInputReplayResult(
        stateId = candidate.stateId, status = CallsSavedInputReplayStatus.UNSUPPORTED, diagnostic = reason,
    )

private class UnsupportedSavedInputBinding(message: String) : IllegalArgumentException(message)

private data class SavedInputBindingPlan(
    val modules: Map<String, TypeScriptSavedInputBindings>,
    val classes: Map<String, Pair<String, String>>,
    val targetSignature: String,
)

private fun savedInputBindingPlan(
    sourceFile: String,
    targetSignature: String,
    candidate: CallsClassInputCandidate,
    closureFiles: Set<String>,
): SavedInputBindingPlan {
    val projectName = targetSignature.removePrefix("@").substringBefore('/')
    val classes = linkedMapOf<String, Pair<String, String>>()
    val statics = linkedMapOf<String, MutableSet<String>>()
    val modules = linkedMapOf<String, MutableSet<String>>()

    fun classBinding(signature: String, file: String, name: String) {
        val expected = EtsClassSignature(name = name, file = EtsFileSignature(projectName = projectName, fileName = file))
        if (signature != expected.toString() || file !in closureFiles) {
            throw UnsupportedSavedInputBinding("Exact top-level source prototype is unavailable: $signature ($file)")
        }
        val binding = file to name
        val previous = classes.putIfAbsent(signature, binding)
        require(previous == null || previous == binding) { "Conflicting concrete prototype metadata: $signature" }
    }

    classBinding(signature = targetSignature, file = sourceFile, name = candidate.className)
    for (node in candidate.inputs.objects) {
        val signature = node.classSignature ?: continue
        classBinding(
            signature = signature, file = checkNotNull(node.classSourceFile), name = checkNotNull(node.className),
        )
    }
    for (field in candidate.inputs.environmentFields) {
        when (field.kind) {
            CallsConcreteEnvironmentFieldKind.STATIC_FIELD -> {
                classBinding(signature = field.ownerSignature, file = field.sourceFile, name = field.ownerName)
                statics.getOrPut(field.ownerSignature) { linkedSetOf() }.add(field.name)
            }
            CallsConcreteEnvironmentFieldKind.MODULE_BINDING -> {
                val owner = EtsClassSignature(
                    name = DEFAULT_ARK_CLASS_NAME,
                    file = EtsFileSignature(projectName = projectName, fileName = field.sourceFile),
                )
                if (field.ownerSignature != owner.toString() || field.ownerName != DEFAULT_ARK_CLASS_NAME ||
                    field.sourceFile !in closureFiles
                ) {
                    throw UnsupportedSavedInputBinding("Exact module binding is unavailable: ${field.ownerSignature}")
                }
                modules.getOrPut(field.sourceFile) { linkedSetOf() }.add(field.name)
            }
        }
    }
    val files = classes.values.map { it.first }.toSet() + modules.keys
    val bindings = files.associateWith { file ->
        TypeScriptSavedInputBindings(
            exportName = "__usvmSavedInput${UUID.randomUUID().toString().replace("-", "")}",
            classes = classes.filterValues { it.first == file }.map { (signature, binding) ->
                TypeScriptSavedInputClass(name = binding.second, staticFields = statics[signature].orEmpty().toList())
            },
            moduleBindings = modules[file].orEmpty().toList(),
        )
    }
    return SavedInputBindingPlan(modules = bindings, classes = classes, targetSignature = targetSignature)
}

private fun savedInputHarness(candidate: CallsClassInputCandidate, plan: SavedInputBindingPlan): String {
    val json = CallsExperimentJson.json
    val aliases = plan.modules.keys.withIndex().associate { (index, file) -> file to "module$index" }
    val imports = plan.modules.map { (file, bindings) ->
        "import { ${bindings.exportName} as ${aliases.getValue(file)} } from ${json.encodeToUtf8SafeString("./$file")};"
    }
    val classes = plan.classes.map { (signature, binding) ->
        val (file, name) = binding
        "[${json.encodeToUtf8SafeString(signature)}, ${aliases.getValue(file)}.classes[${json.encodeToUtf8SafeString(name)}]]"
    }
    val environmentSetters = candidate.inputs.environmentFields.map { field ->
        val module = aliases.getValue(field.sourceFile)
        val key = json.encodeToUtf8SafeString(field.ownerSignature)
        "if (field.ownerSignature === $key) { $module.writeBinding(field.ownerName, field.name, value); return; }"
    }.distinct()
    val owner = "classes.get(${json.encodeToUtf8SafeString(plan.targetSignature)})"
    val method = json.encodeToUtf8SafeString(candidate.methodName)
    val invocation = when (candidate.invocationKind) {
        CallsClassInvocationKind.CONSTRUCTOR -> "Reflect.construct($owner, restored.arguments)"
        CallsClassInvocationKind.STATIC_METHOD -> "Reflect.apply($owner[$method], $owner, restored.arguments)"
        CallsClassInvocationKind.INSTANCE_METHOD ->
            "Reflect.apply(restored.receiver[$method], restored.receiver, restored.arguments)"
    }
    return """
        ${imports.joinToString("\n")}
        ${callsConcretePrimitiveDecoderSource()}
        ${callsConcreteHeapRestoreSource()}
        ${classReplayValueEncoderSource()}
        export function invokeSavedInput(): any {
          const candidate = JSON.parse(${json.encodeToUtf8SafeString(encodeCallsArtifact(candidate))});
          const classes = new Map<string, any>([${classes.joinToString()}]);
          const prototypes = new Map(Array.from(classes, ([signature, owner]) => [signature, owner.prototype] as const));
          const restored = restoreCallsConcreteHeap(candidate.inputs, prototypes, decodeCallsConcretePrimitive,
              (field, value) => {
            ${environmentSetters.joinToString("\n")}
            throw Error('Unknown exact saved input environment owner: ' + field.ownerSignature);
          });
          let returned: any;
          try { returned = $invocation; } catch (error) {
            const diagnostic = error instanceof Error ? error.name + ': ' + error.message : 'Thrown ' + typeof error;
            return { completion: 'THREW', returnValue: '', returnValueRepresentable: false, diagnostic };
          }
          try {
            return { completion: 'RETURNED', returnValue: JSON.stringify(encode(returned)),
                     returnValueRepresentable: true, diagnostic: '' };
          } catch (error) {
            const diagnostic = error instanceof Error ? error.message : 'Unrepresentable return value';
            return { completion: 'RETURNED', returnValue: '', returnValueRepresentable: false, diagnostic };
          }
        }
    """.trimIndent()
}

private const val SAVED_INPUT_REPLAY_ARGUMENT_COUNT = 6
