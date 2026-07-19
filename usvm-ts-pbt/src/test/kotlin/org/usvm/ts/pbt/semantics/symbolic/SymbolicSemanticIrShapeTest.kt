package org.usvm.ts.pbt.semantics.symbolic

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "ETS_FRONTEND_DIR", matches = ".+")
class SymbolicSemanticIrShapeTest {
    @Test
    fun `native frontend keeps module callable and iterator evidence explicit`() {
        val root: Path = Paths.get(
            requireNotNull(javaClass.getResource("/symbolic-semantics/collections")) {
                "symbolic semantics fixture directory is missing"
            }.toURI(),
        )
        val scene = loadEtsProjectAutoConvert(root)
        val arraysFile = scene.projectFiles.single { it.signature.fileName.endsWith("arrays.ts") }
        val builtinsFile = scene.projectFiles.single { it.signature.fileName.endsWith("builtins.ts") }

        assertTrue(arraysFile.importInfos.any { it.name == "util" && it.isNamespaceImport })
        val indexOf = arraysFile.method("indexOf")
        assertTrue(
            indexOf.assignments().any {
                (it.rhv as? EtsInstanceFieldRef)?.field?.name == "defaultEquals"
            },
        )
        assertTrue(indexOf.assignments().any { it.rhv is EtsPtrCallExpr })

        val forEach = arraysFile.method("forEach")
        assertTrue(
            forEach.assignments().any {
                (it.rhv as? EtsInstanceCallExpr)?.callee?.name == "Symbol.iterator"
            },
        )
        assertTrue(forEach.assignments().any { (it.rhv as? EtsInstanceCallExpr)?.callee?.name == "next" })
        assertTrue(forEach.assignments().any { (it.rhv as? EtsInstanceFieldRef)?.field?.name == "done" })
        assertTrue(forEach.assignments().any { (it.rhv as? EtsInstanceFieldRef)?.field?.name == "value" })

        val arrayIsArray = builtinsFile.method("arrayIsArray")
        assertTrue(
            arrayIsArray.assignments().any {
                (it.rhv as? EtsInstanceCallExpr)?.callee?.name == "isArray"
            },
        )
        val objectTag = builtinsFile.method("objectToStringTag")
        assertTrue(objectTag.assignments().any { (it.rhv as? EtsInstanceCallExpr)?.callee?.name == "call" })
    }

    private fun EtsFile.method(name: String): EtsMethod = classes
        .flatMap { it.methods }
        .single { it.name == name }

    private fun EtsMethod.assignments(): List<EtsAssignStmt> = cfg.stmts.filterIsInstance<EtsAssignStmt>()
}
