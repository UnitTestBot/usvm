package org.usvm.samples

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import java.util.IdentityHashMap
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test

class Enums : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test enumOrdinal`() {
        val method = getMethod(className, "enumOrdinal")
        (method as EtsMethodImpl)._cfg = scene.fixEnums(method.cfg)
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }
}

fun EtsScene.fixEnums(cfg: EtsCfg): EtsCfg {
    val stmt2new: MutableMap<EtsStmt, EtsStmt> = IdentityHashMap()
    val stmt2old: MutableMap<EtsStmt, EtsStmt> = IdentityHashMap()

    val classes: MutableMap<String, EtsClass> = hashMapOf()
    // TODO: handle multiple classes with the same name
    for (cls in projectAndSdkClasses) {
        classes[cls.name] = cls
    }

    for (stmt in cfg.stmts) {
        if (stmt is EtsAssignStmt
            && stmt.rhv is EtsInstanceFieldRef
            && (stmt.rhv as EtsInstanceFieldRef).instance.name in classes
        ) {
            val rhv = stmt.rhv as EtsInstanceFieldRef
            val cls = classes.getValue(rhv.instance.name)
            val field = cls.fields.single { it.name == rhv.field.name }
            val newRhv = EtsStaticFieldRef(field.signature)
            val newStmt = EtsAssignStmt(stmt.location, stmt.lhv, newRhv)
            stmt2new[stmt] = newStmt
            stmt2old[newStmt] = stmt
        } else {
            stmt2new[stmt] = stmt
            stmt2old[stmt] = stmt
        }
    }

    @Suppress("UNCHECKED_CAST")
    val successors = EtsCfg::class
        .memberProperties
        .firstOrNull { it.name == "successorMap" }
        ?.apply { isAccessible = true }
        ?.get(cfg) as? Map<EtsStmt, List<EtsStmt>>
    checkNotNull(successors)

    val newStmts = cfg.stmts.map { stmt2new.getValue(it) }
    val newSuccessors = newStmts.associateWith { newStmt ->
        val oldStmt = stmt2old.getValue(newStmt)
        successors.getValue(oldStmt).map { stmt2new.getValue(it) }
    }

    return EtsCfg(
        stmts = newStmts,
        successorMap = newSuccessors,
    )
}
