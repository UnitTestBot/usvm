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

// TODO: use deterministic ids to compare states
private fun <T> compareByHash(): Comparator<T> = compareBy { it.hashCode() }

fun <State : UState<*, *, *, *>> createWeightedPathSelector(type: WeightType, random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(compareByHash()) }) { it.path.size }
    }

    // NB: Random never returns 1.0!
    return WeightedPathSelector({ DiscretePdf(compareByHash()) { random.nextFloat() } }) { 1f / max(it.path.size, 1) }
}
