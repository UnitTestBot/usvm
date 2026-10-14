package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.fastcheck.TypeScriptClassStatementDescriptor
import org.usvm.ts.pbt.fastcheck.TypeScriptClassSurfaceInventory
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import java.nio.file.Files
import java.nio.file.Path

/** Instrument only a source overlay; the original source and frozen AST offsets remain untouched. */
@Serializable
internal data class CallsClassSourceInstrumentation(
    val universe: CallsClassSourceUniverse,
    val instrumentedSource: String,
    val instrumentedSha256: String,
    val hitGlobalName: String,
    val inventory: TypeScriptClassSurfaceInventory,
) {
    init {
        require(callsClassSourceSha256(instrumentedSource.toByteArray(Charsets.UTF_8)) == instrumentedSha256) {
            "Class instrumentation source hash mismatch"
        }
    }

    fun validateSurface(surface: CallsClassSurface) {
        require(surface.sourceFile == universe.sourceFile && surface.exportName == universe.className) {
            "Class harness surface selects a different source class"
        }
        require(surface.constructorInputs.map { input -> input.name } == inventory.constructorParameterNames) {
            "Class harness must represent every constructor parameter in source order"
        }
        val expectedMembers = inventory.publicMembers.map { member ->
            Triple(member.name, member.kind, member.isStatic)
        }.toSet()
        val actualMembers = surface.members.map { member ->
            Triple(member.name, member.kind.name, member.isStatic)
        }.toSet()
        require(actualMembers == expectedMembers && actualMembers.size == surface.members.size) {
            "Class harness must represent the complete original public callable surface"
        }
        for (member in surface.members) {
            val original = inventory.publicMembers.single { original ->
                original.name == member.name && original.kind == member.kind.name &&
                    original.isStatic == member.isStatic
            }
            require(member.inputs.map { input -> input.name } == original.parameterNames) {
                "Class harness must represent every parameter of ${member.name} in source order"
            }
        }
        val hasUnobservablePrivateState = inventory.instanceDataFields.any { field -> field.isEcmaPrivate }
        require(!hasUnobservablePrivateState) { "ECMAScript private receiver state cannot be observed by this harness" }
        val expectedFields = inventory.instanceDataFields.map { field -> field.name }.toSet()
        require(surface.stateFields.keys == expectedFields) {
            "Class harness must observe every original receiver data field: $expectedFields"
        }
        validateDataClasses(surface)
    }

    private fun validateDataClasses(surface: CallsClassSurface) {
        val duplicateExports = surface.dataClasses.map { dataClass -> dataClass.exportName }.distinct().size !=
            surface.dataClasses.size
        require(!duplicateExports) { "Companion data-class exports must be unique" }
        for (dataClass in surface.dataClasses) {
            require(dataClass.exportName != universe.className) { "The target class has its own observation identity" }
            val original = inventory.dataClasses.singleOrNull { original ->
                original.exportName == dataClass.exportName
            }
            requireNotNull(original) {
                "Unverified companion data class ${dataClass.exportName} in ${universe.sourceFile}"
            }
            require(dataClass.fields.keys == original.fields.toSet()) {
                "Companion data class ${dataClass.exportName} must preserve every original own data field"
            }
        }
    }

    companion object {
        fun create(sourceRoot: Path, sourceFile: String, className: String): CallsClassSourceInstrumentation {
            val root = sourceRoot.toRealPath()
            val source = root.resolve(sourceFile).normalize().toRealPath()
            require(source.startsWith(root)) { "Class source is outside the frozen source root" }
            val sourceBytes = Files.readAllBytes(source)
            val sourceSha256 = callsClassSourceSha256(sourceBytes)
            val inspected = TypeScriptSourceInspector.instrumentClassCoverage(source = source, className = className)
            val instrumentedSource = requireNotNull(inspected.source) {
                inspected.reason ?: "Unsupported class statement instrumentation"
            }
            require(inspected.sourceSha256 == sourceSha256) { "Class source changed during AST inspection" }

            val universeIdentity = CallsExperimentJson.json.encodeToString(
                CallsClassUniverseHashInput(
                    sourceFile = sourceFile,
                    className = className,
                    sourceSha256 = sourceSha256,
                    instrumenterVersion = inspected.instrumenterVersion,
                    statements = inspected.statements,
                    inventory = requireNotNull(inspected.inventory),
                ),
            )
            val universe = CallsClassSourceUniverse(
                sourceFile = sourceFile,
                className = className,
                sourceSha256 = sourceSha256,
                universeSha256 = callsClassSourceSha256(universeIdentity.toByteArray(Charsets.UTF_8)),
                instrumenterVersion = inspected.instrumenterVersion,
                statements = inspected.statements,
            )

            return CallsClassSourceInstrumentation(
                universe = universe,
                instrumentedSource = instrumentedSource,
                instrumentedSha256 = requireNotNull(inspected.instrumentedSha256),
                hitGlobalName = inspected.counterGlobalName,
                inventory = requireNotNull(inspected.inventory),
            )
        }
    }
}

@Serializable
private data class CallsClassUniverseHashInput(
    val sourceFile: String,
    val className: String,
    val sourceSha256: String,
    val instrumenterVersion: String,
    val statements: List<TypeScriptClassStatementDescriptor>,
    val inventory: TypeScriptClassSurfaceInventory,
)
