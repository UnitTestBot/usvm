@file:Suppress("PropertyName")

package org.usvm.util

import org.usvm.model.TsEntity
import org.usvm.model.TsStmt

class EntityCollector<R : Any, C : MutableCollection<R>>(
    val result: C,
    val block: (TsEntity) -> R?,
) : AbstractHandler() {
    override fun handle(value: TsEntity) {
        val item = block(value)
        if (item != null) {
            result += item
        }
    }

    override fun handle(stmt: TsStmt) {
        // Do nothing.
    }
}

fun <R : Any, C : MutableCollection<R>> TsEntity.collectEntitiesTo(
    destination: C,
    block: (TsEntity) -> R?,
): C {
    accept(EntityCollector(destination, block))
    return destination
}

fun <R : Any, C : MutableCollection<R>> TsStmt.collectEntitiesTo(
    destination: C,
    block: (TsEntity) -> R?,
): C {
    accept(EntityCollector(destination, block))
    return destination
}
