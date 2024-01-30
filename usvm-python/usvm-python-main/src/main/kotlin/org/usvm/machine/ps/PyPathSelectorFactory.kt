package org.usvm.machine.ps

import mu.KLogging
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.machine.PyContext
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.ps.strategies.impls.BaselineActionStrategy
import org.usvm.machine.ps.strategies.impls.BaselineDFGraphCreation
import org.usvm.machine.ps.strategies.impls.BaselineDelayedForkStrategy
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.WeightedPathSelector
import org.usvm.python.model.calculateNumberOfMocks
import kotlin.math.max
import kotlin.random.Random

private val logger = object : KLogging() {}.logger

enum class PyPathSelectorType {
    BaselineDfs,
    BaselineWeightedByNumberOfVirtual
}

fun createPyPathSelector(
    type: PyPathSelectorType,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    when (type) {
        PyPathSelectorType.BaselineDfs ->
            createBaselineDfsPyPathSelector(ctx, random, newStateObserver)
        PyPathSelectorType.BaselineWeightedByNumberOfVirtual ->
            createBaselineWeightedByNumberOfVirtualPyPathSelector(ctx, random, newStateObserver)
    }

fun createBaselineDfsPyPathSelector(
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

fun createBaselineWeightedByNumberOfVirtualPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        BaselineActionStrategy(random),
        BaselineDelayedForkStrategy,
        BaselineDFGraphCreation {
            WeightedPathSelector(
                priorityCollectionFactory = {
                    RandomizedPriorityCollection(compareBy { it.id }) { random.nextDouble() }
                },
                weighter = {
                    val modelHolder = PyModelHolder(it.pyModel)
                    val builder = PyObjectModelBuilder(it, modelHolder)
                    val models = it.inputSymbols.map { symbol ->
                        val interpreted = interpretSymbolicPythonObject(modelHolder, it.memory, symbol)
                        builder.convert(interpreted)
                    }
                    val mocks = models.fold(0) { acc, obj -> acc + calculateNumberOfMocks(obj) }
                    logger.debug { "Mocks of state $it: $mocks" }
                    1.0 / max(1, 4 * mocks * mocks)
                }
            )
        },
        newStateObserver
    )