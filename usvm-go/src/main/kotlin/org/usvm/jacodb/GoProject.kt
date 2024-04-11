package org.usvm.jacodb

import org.jacodb.api.core.Project

class GoProject(
    val methods: List<GoMethod>
) : Project<GoType> {
    override fun close() {}

    override fun findTypeOrNull(name: String): GoType? {
        // return class or interface or null if there is no such class found in locations
        methods.forEach {
            it.blocks.forEach { block ->
                block.insts.forEach { inst ->
                    inst.operands.forEach { expr ->
                        if (expr.toString() == name) {
                            return expr.type
                        }
                    }
                }
            }
        }
        return null
    }
}
