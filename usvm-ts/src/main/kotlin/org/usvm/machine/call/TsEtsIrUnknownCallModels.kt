package org.usvm.machine.call

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes

private const val BYTE_MASK = 0xff
private val sha256Regex = Regex("[0-9a-f]{64}")

/** Reproducible native-frontend artifact for one stable TypeScript semantic-model entry point. */
data class TsEtsIrUnknownCallModelArtifact(
    val file: EtsFile,
    val entryPoint: EtsMethod,
    val sourceHash: String,
    val etsIrHash: String,
) {
    val implementationKind: TsUnknownCallModelImplementationKind
        get() = TsUnknownCallModelImplementationKind.ETS_IR_BODY

    init {
        require(sourceHash.matches(sha256Regex)) { "TypeScript model source hash must be a lowercase SHA-256" }
        require(etsIrHash.matches(sha256Regex)) { "TypeScript model EtsIR hash must be a lowercase SHA-256" }
    }
}

/** Loads one TypeScript model source with JacoDB's bundled native TypeScript frontend. */
fun loadEtsIrUnknownCallModelArtifact(
    sourcePath: Path,
    entryPointClassName: String,
    entryPointMethodName: String,
): TsEtsIrUnknownCallModelArtifact = loadEtsIrUnknownCallModelArtifact(
    sourcePath = sourcePath,
    entryPointClassName = entryPointClassName,
    entryPointMethodName = entryPointMethodName,
    generateIr = { path ->
        generateEtsIR(
            projectPath = path,
            isProject = false,
            loadEntrypoints = true,
            useArkAnalyzerTypeInference = null,
            provider = EtsIrProvider.TS_FRONTEND,
        )
    },
)

internal fun loadEtsIrUnknownCallModelArtifact(
    sourcePath: Path,
    entryPointClassName: String,
    entryPointMethodName: String,
    generateIr: (Path) -> Path,
): TsEtsIrUnknownCallModelArtifact {
    val sourceBytes = sourcePath.readBytes()
    val irPath = generateIr(sourcePath)

    return try {
        check(sourcePath.readBytes().contentEquals(sourceBytes)) {
            "TypeScript model source changed while generating EtsIR: $sourcePath"
        }

        val irBytes = irPath.readBytes()
        val file = irPath.inputStream().use { stream ->
            EtsFileDto.loadFromJson(stream).toEtsFile()
        }
        val entryPointClass = file.allClasses.singleOrNull { it.name == entryPointClassName }
            ?: error("Expected one TypeScript model class named $entryPointClassName")
        val entryPoint = entryPointClass.methods.singleOrNull { it.name == entryPointMethodName }
            ?: error("Expected one TypeScript model entry point named $entryPointClassName::$entryPointMethodName")
        check(entryPoint.isStatic) {
            "TypeScript model entry point $entryPointClassName::$entryPointMethodName must be static"
        }
        check(entryPoint.cfg.instructions.isNotEmpty()) {
            "TypeScript model entry point $entryPointClassName::$entryPointMethodName must have a body"
        }

        TsEtsIrUnknownCallModelArtifact(
            file = file,
            entryPoint = entryPoint,
            sourceHash = sourceBytes.sha256(),
            etsIrHash = irBytes.sha256(),
        )
    } finally {
        irPath.deleteIfExists()
    }
}

/** Registry handle for a TypeScript semantic model compiled to EtsIR. */
class TsEtsIrUnknownCallModelImplementation(
    val artifact: TsEtsIrUnknownCallModelArtifact,
    val domainGuard: TsEtsIrUnknownCallModelDomainGuard = TsEtsIrUnknownCallModelDomainGuard.ALWAYS,
) : TsUnknownCallModelImplementation {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.ETS_IR_BODY
    override val fingerprintComponents: List<String> =
        listOf(
            artifact.entryPoint.signature.enclosingClass.file.toString(),
            artifact.entryPoint.signature.toString(),
            artifact.sourceHash,
            artifact.etsIrHash,
        )
    override val additionalSceneFiles: List<EtsFile> = listOf(artifact.file)
}

/** Builds the symbolic guard for inputs supported by one EtsIR model body. */
fun interface TsEtsIrUnknownCallModelDomainGuard {
    fun evaluate(
        state: TsState,
        call: TsUnknownCall,
        inputs: List<UExpr<*>>,
    ): UBoolExpr

    companion object {
        val ALWAYS = TsEtsIrUnknownCallModelDomainGuard { state, _, _ -> state.ctx.trueExpr }
    }
}

/** Executes TypeScript semantic-model bodies through the normal EtsIR interpreter. */
object TsEtsIrUnknownCallModelBackend : TsUnknownCallModelBackend {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.ETS_IR_BODY

    override fun execute(
        implementation: TsUnknownCallModelImplementation,
        precision: TsUnknownCallModelPrecision,
        state: TsState,
        call: TsUnknownCall,
    ): TsUnknownCallModelBackendResult {
        val etsIrImplementation = requireNotNull(implementation as? TsEtsIrUnknownCallModelImplementation) {
            "ETS_IR_BODY backend requires TsEtsIrUnknownCallModelImplementation, got ${implementation::class}"
        }
        val inputs = call.resolvedInputs()
        if (inputs == null || inputs.size != etsIrImplementation.artifact.entryPoint.parameters.size) {
            return when (precision) {
                TsUnknownCallModelPrecision.EXACT -> TsUnknownCallModelBackendResult.NotApplicable
                TsUnknownCallModelPrecision.PARTIAL -> TsUnknownCallModelBackendResult.Executed(
                    execution = unsupportedExecution(state),
                )
            }
        }

        val domainGuard = etsIrImplementation.domainGuard.evaluate(
            state = state,
            call = call,
            inputs = inputs,
        )
        if (precision == TsUnknownCallModelPrecision.EXACT && domainGuard != state.ctx.trueExpr) {
            return TsUnknownCallModelBackendResult.NotApplicable
        }

        val successor = TsUnknownCallModelSuccessor(
            guard = domainGuard,
            completion = TsUnknownCallModelCompletion.EtsIrBody(
                entryPoint = etsIrImplementation.artifact.entryPoint,
                inputs = inputs,
            ),
        )

        return TsUnknownCallModelBackendResult.Executed(
            execution = TsUnknownCallModelExecution(
                successors = listOf(successor),
                residualGuard = when (precision) {
                    TsUnknownCallModelPrecision.EXACT -> null
                    TsUnknownCallModelPrecision.PARTIAL -> state.ctx.mkNot(domainGuard)
                },
            ),
        )
    }

    private fun unsupportedExecution(state: TsState): TsUnknownCallModelExecution {
        val unreachableSuccessor = TsUnknownCallModelSuccessor(
            guard = state.ctx.falseExpr,
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )

        return TsUnknownCallModelExecution(
            successors = listOf(unreachableSuccessor),
            residualGuard = state.ctx.trueExpr,
        )
    }
}

private fun TsUnknownCall.resolvedInputs(): List<UExpr<*>>? = buildList {
    receiver?.let { receiver -> add(receiver.resolved ?: return null) }
    arguments.forEach { argument -> add(argument.resolved ?: return null) }
}

internal fun TsState.enterEtsIrUnknownCallModel(
    modelId: String,
    entryPoint: EtsMethod,
    inputs: List<UExpr<*>>,
    returnSite: EtsStmt,
) {
    val modelClass = requireNotNull(entryPoint.enclosingClass) {
        "EtsIR semantic-model entry point must belong to a class"
    }
    val arguments = buildList {
        add(getStaticInstance(modelClass))
        addAll(inputs)
    }

    check(inputs.size == entryPoint.parameters.size) {
        "Expected ${entryPoint.parameters.size} EtsIR model inputs, got ${inputs.size}"
    }

    registerCallee(returnSite, entryPoint.cfg)
    enterUnknownCallModel(modelId = modelId, entryPoint = entryPoint)
    pushSortsForActualArguments(arguments)
    callStack.push(entryPoint, returnSite)
    memory.stack.push(arguments.toTypedArray(), entryPoint.localsCount)
    newStmt(entryPoint.cfg.instructions.first())
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
