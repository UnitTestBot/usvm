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
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readText

/** Native-frontend artifact for one TypeScript semantic-model entry point. */
data class TsEtsIrUnknownCallModelArtifact(
    val file: EtsFile,
    val entryPoint: EtsMethod,
    internal val etsIrJson: String,
) {
    internal fun materializeFile(): EtsFile =
        etsIrJson.byteInputStream().use { stream ->
            EtsFileDto.loadFromJson(stream).toEtsFile()
        }

    internal fun materializeWith(file: EtsFile): TsEtsIrUnknownCallModelArtifact {
        val entryPointClassName = requireNotNull(entryPoint.enclosingClass) {
            "EtsIR semantic-model entry point must belong to a class"
        }.name
        val materializedEntryPoint = findEntryPoint(
            file = file,
            entryPointClassName = entryPointClassName,
            entryPointMethodName = entryPoint.name,
        )

        return copy(file = file, entryPoint = materializedEntryPoint)
    }
}

/** Loads one TypeScript model source with JacoDB's bundled native TypeScript frontend. */
fun loadEtsIrUnknownCallModelArtifact(
    sourcePath: Path,
    entryPointClassName: String,
    entryPointMethodName: String,
): TsEtsIrUnknownCallModelArtifact {
    val irPath = generateEtsIR(
        projectPath = sourcePath,
        isProject = false,
        loadEntrypoints = true,
        useArkAnalyzerTypeInference = null,
        provider = EtsIrProvider.TS_FRONTEND,
    )

    return try {
        val etsIrJson = irPath.readText()
        val file = etsIrJson.byteInputStream().use { stream ->
            EtsFileDto.loadFromJson(stream).toEtsFile()
        }
        val entryPoint = findEntryPoint(file, entryPointClassName, entryPointMethodName)

        TsEtsIrUnknownCallModelArtifact(
            file = file,
            entryPoint = entryPoint,
            etsIrJson = etsIrJson,
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
    val inputAdapter: TsEtsIrUnknownCallModelInputAdapter = TsEtsIrUnknownCallModelInputAdapter.IDENTITY,
) : TsUnknownCallModel, TsMachineLocalUnknownCallModel {
    override val additionalSceneFiles: List<EtsFile> = listOf(artifact.file)

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        val inputs = inputAdapter.adapt(
            state = state,
            call = call,
        ) ?: return null
        if (inputs.size != artifact.entryPoint.parameters.size) {
            return null
        }

        val guard = domainGuard.evaluate(
            state = state,
            call = call,
            inputs = inputs,
        )
        if (guard.isFalse) {
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
            residualGuard = guard.takeUnless { it.isTrue }?.let(state.ctx::mkNot),
        )
    }

    override fun materializeForMachine(
        materializedFiles: IdentityHashMap<EtsFile, EtsFile>,
    ): TsUnknownCallModel {
        val file = materializedFiles.getOrPut(artifact.file, artifact::materializeFile)
        val materializedArtifact = artifact.materializeWith(file)

        return TsEtsIrUnknownCallModel(
            id = id,
            target = target,
            artifact = materializedArtifact,
            domainGuard = domainGuard,
            inputAdapter = inputAdapter,
        )
    }
}

/** Adapts resolved call inputs to the parameters of a TypeScript model entry point. */
fun interface TsEtsIrUnknownCallModelInputAdapter {
    fun adapt(
        state: TsState,
        call: TsUnknownCall,
    ): List<UExpr<*>>?

    companion object {
        val IDENTITY = TsEtsIrUnknownCallModelInputAdapter { _, call -> call.resolvedInputs() }
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

private fun findEntryPoint(
    file: EtsFile,
    entryPointClassName: String,
    entryPointMethodName: String,
): EtsMethod {
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

    return entryPoint
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
