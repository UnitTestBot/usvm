package org.usvm.machine.ps

import org.usvm.machine.PyContext
import org.usvm.machine.ps.strategies.impls.BaselineActionStrategy
import org.usvm.machine.ps.strategies.impls.BaselineDFGraphCreation
import org.usvm.machine.ps.strategies.impls.BaselineDelayedForkStrategy
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.ps.DfsPathSelector
import kotlin.random.Random

fun createBaselinePyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        BaselineActionStrategy(random),
        BaselineDelayedForkStrategy,
        BaselineDFGraphCreation { DfsPathSelector() },
        newStateObserver
    )