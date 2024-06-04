package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaInst
import org.usvm.PathNode

// TODO rewrite it
data class PandaTest(
    val parameters: List<Any>,
    val resultValue: Any?,
    val trace: PathNode<PandaInst>? = null
)


data class PandaClassCoverage(
    val visitedStmts: Set<PandaInst>,
)
