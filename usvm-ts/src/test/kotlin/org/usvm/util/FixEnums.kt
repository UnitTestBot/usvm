package org.usvm.util

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getDeclaredLocals
import java.util.IdentityHashMap
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun EtsScene.fixEnums(cfg: EtsCfg): EtsCfg {
    val stmt2new: MutableMap<EtsStmt, EtsStmt> = IdentityHashMap()
    val stmt2old: MutableMap<EtsStmt, EtsStmt> = IdentityHashMap()

    val classesByName: MutableMap<String, MutableList<EtsClass>> = hashMapOf()
    // TODO: handle multiple classes with the same name
    for (cls in projectAndSdkClasses) {
        classesByName.getOrPut(cls.name) { mutableListOf() }.add(cls)
    }

    fun handle(stmt: EtsStmt) {
        if (stmt is EtsAssignStmt) {
            val rhv = stmt.rhv
            // fix enums
            if (rhv is EtsInstanceFieldRef) {
                val classes = classesByName[rhv.instance.name]
                if (classes != null) {
                    val fields = classes.flatMap { it.fields }.filter { it.name == rhv.field.name }
                    if (fields.size == 1) {
                        val field = fields.single()
                        val newRhv = EtsStaticFieldRef(field.signature)
                        val newStmt = EtsAssignStmt(stmt.location, stmt.lhv, newRhv)
                        stmt2new[stmt] = newStmt
                        stmt2old[newStmt] = stmt
                        return
                    }
                }
            }
            // fix statics
            if (rhv is EtsInstanceCallExpr) {
                if (rhv.instance !in stmt.method.getDeclaredLocals()) {
                    val classes = classesByName[rhv.instance.name]
                    if (classes != null) {
                        val methods = classes.flatMap { it.methods }.filter { it.name == rhv.method.name }
                        if (methods.size == 1) {
                            val method = methods.single()
                            val newRhv = EtsStaticCallExpr(method.signature, rhv.args)
                            val newStmt = EtsAssignStmt(stmt.location, stmt.lhv, newRhv)
                            stmt2new[stmt] = newStmt
                            stmt2old[newStmt] = stmt
                            return
                        }
                    }
                }
            }
        }

        // fix statics
        if (stmt is EtsCallStmt) {
            val expr = stmt.expr
            if (expr is EtsInstanceCallExpr) {
                if (expr.instance !in stmt.method.getDeclaredLocals()) {
                    val classes = classesByName[expr.instance.name]
                    if (classes != null) {
                        val methods = classes.flatMap { it.methods }.filter { it.name == expr.method.name }
                        if (methods.size == 1) {
                            val method = methods.single()
                            val newExpr = EtsStaticCallExpr(method.signature, expr.args)
                            val newStmt = EtsCallStmt(stmt.location, newExpr)
                            stmt2new[stmt] = newStmt
                            stmt2old[newStmt] = stmt
                            return
                        }
                    }
                }
            }
        }

        stmt2new[stmt] = stmt
        stmt2old[stmt] = stmt
    }

    for (stmt in cfg.stmts) {
        handle(stmt)
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
