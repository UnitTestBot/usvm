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
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

private const val BYTE_MASK = 0xff
private val sha256Regex = Regex("[0-9a-f]{64}")

/** Reproducible native-frontend artifact for one TypeScript semantic-model entry point. */
data class TsEtsIrUnknownCallModelArtifact(
    val file: EtsFile,
    val entryPoint: EtsMethod,
    val sourceHash: String,
    val etsIrHash: String,
) {
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

internal fun loadBundledEtsIrUnknownCallModelArtifact(
    resourceName: String,
    sourceFileName: String,
    entryPointClassName: String,
    entryPointMethodName: String,
): TsEtsIrUnknownCallModelArtifact {
    val sourceDirectory = createTempDirectory(prefix = "usvm-ts-model-")
    val sourcePath = sourceDirectory.resolve(sourceFileName)

    return try {
        val source = checkNotNull(TsEtsIrUnknownCallModel::class.java.getResourceAsStream(resourceName)) {
            "Bundled TypeScript semantic model resource not found: $resourceName"
        }
        source.use { input ->
            sourcePath.outputStream().use { output -> input.copyTo(output) }
        }

        loadEtsIrUnknownCallModelArtifact(
            sourcePath = sourcePath,
            entryPointClassName = entryPointClassName,
            entryPointMethodName = entryPointMethodName,
        )
    } finally {
        sourcePath.deleteIfExists()
        sourceDirectory.deleteIfExists()
    }
}

/** A model body written in TypeScript and executed by the normal EtsIR interpreter. */
class TsEtsIrUnknownCallModel(
    override val id: String,
    override val target: TsUnknownCallTarget,
    val artifact: TsEtsIrUnknownCallModelArtifact,
    val domainGuard: TsEtsIrUnknownCallModelDomainGuard = TsEtsIrUnknownCallModelDomainGuard.ALWAYS,
) : TsUnknownCallModel {
    override val additionalSceneFiles: List<EtsFile> = listOf(artifact.file)

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        val inputs = call.resolvedInputs() ?: return null
        if (inputs.size != artifact.entryPoint.parameters.size) {
            return null
        }

        val guard = domainGuard.evaluate(
            state = state,
            call = call,
            inputs = inputs,
        )
        if (guard == state.ctx.falseExpr) {
            return null
        }

        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.EtsIrBody(
                entryPoint = artifact.entryPoint,
                inputs = inputs,
            ),
        )

        return TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = guard.takeUnless { it == state.ctx.trueExpr }?.let(state.ctx::mkNot),
        )
    }
}

/** Builds the symbolic input guard for one TypeScript model body. */
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
    enterUnknownCallModel(modelId)
    pushSortsForActualArguments(arguments)
    callStack.push(entryPoint, returnSite)
    memory.stack.push(arguments.toTypedArray(), entryPoint.localsCount)
    newStmt(entryPoint.cfg.instructions.first())
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
