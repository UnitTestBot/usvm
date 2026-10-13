package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsStmt
import java.nio.file.Path

/** Selects symbolic candidate novelty; original-source coverage still uses its separate frozen universe. */
@Serializable
internal data class CallsSymbolicCoverageClass(
    val sourceFile: String,
    val className: String,
) {
    init {
        val sourcePath = Path.of(sourceFile)
        val isRelativeSource = sourceFile.isNotBlank() && !sourcePath.isAbsolute &&
            sourcePath.normalize() == sourcePath && !sourcePath.startsWith("..") && sourceFile != "."
        require(isRelativeSource) { "Symbolic coverage class source must be a normalized relative path" }
        require(className.isNotBlank()) { "Symbolic coverage class name must not be blank" }
    }
}

internal sealed interface CallsSymbolicCoverageScope {
    data class Selected(
        val classSignature: EtsClassSignature?,
        val statements: Set<EtsStmt>,
    ) : CallsSymbolicCoverageScope

    data class Rejected(
        val status: CallsCoverageSearchStatus,
        val diagnostic: String,
    ) : CallsSymbolicCoverageScope
}

internal fun resolveCallsSymbolicCoverageScope(
    preparation: CallsFunctionPreparation.Eligible,
    target: CallsSymbolicCoverageClass?,
): CallsSymbolicCoverageScope {
    if (target == null) {
        return CallsSymbolicCoverageScope.Selected(
            classSignature = null,
            statements = preparation.method.cfg.stmts.toSet(),
        )
    }

    val targetPath = target.sourceFile.replace('\\', '/')
    val classes = preparation.scene.projectClasses.filter { owner ->
        owner.name == target.className && preparation.sourcePaths[owner.signature.file] == targetPath
    }
    if (classes.size != 1) {
        val status = if (classes.isEmpty()) CallsCoverageSearchStatus.UNMAPPED else CallsCoverageSearchStatus.AMBIGUOUS
        return CallsSymbolicCoverageScope.Rejected(
            status = status,
            diagnostic = "Symbolic coverage class $targetPath:${target.className} resolves to ${classes.size} classes",
        )
    }

    val owner = classes.single()
    val methods = owner.methods.filter { method -> method.signature.enclosingClass == owner.signature }
    val statements = methods.flatMapTo(hashSetOf()) { method -> method.cfg.stmts }
    if (statements.isEmpty()) {
        return CallsSymbolicCoverageScope.Rejected(
            status = CallsCoverageSearchStatus.UNSUPPORTED,
            diagnostic = "Symbolic coverage class $targetPath:${target.className} has no executable statements",
        )
    }

    return CallsSymbolicCoverageScope.Selected(classSignature = owner.signature, statements = statements)
}
