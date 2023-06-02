package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.util.DiscretePdf
import org.usvm.util.VanillaPriorityQueue
import kotlin.math.max
import kotlin.random.Random

enum class WeightType {
    DEPTH
}

fun <State : UState<*, *, *, *>> createWeightedPathSelector(type: WeightType, randomSeed: Int? = null): UPathSelector<State> {
    if (randomSeed == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(compareBy()) }) { it.path.size }
    }

    val random = Random(randomSeed)
    // NB: Random never returns 1.0!
    return WeightedPathSelector({ DiscretePdf({ random.nextFloat() }, compareBy()) }) { 1f / max(it.path.size, 1) }
}
