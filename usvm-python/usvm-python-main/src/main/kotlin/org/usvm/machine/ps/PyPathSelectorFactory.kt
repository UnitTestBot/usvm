package org.usvm.machine.ps

import mu.KLogging
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.ps.strategies.impls.*
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.RandomTreePathSelector
import org.usvm.ps.WeightedPathSelector
import org.usvm.python.model.PyTupleObject
import org.usvm.python.model.calculateNumberOfMocks
import kotlin.math.max
import kotlin.random.Random

private val logger = object : KLogging() {}.logger

enum class PyPathSelectorType {
    BaselinePriorityDfs,
    BaselineWeightedDfs,
    BaselinePriorityWeightedByNumberOfVirtual,
    BaselinePriorityPlusTypeRatingByHintsDfs,
    DelayedForkByInstructionPriorityDfs,
    DelayedForkByInstructionWeightedDfs,
    DelayedForkByInstructionWeightedRandomTree
}

fun createPyPathSelector(
    initialState: PyState,
    type: PyPathSelectorType,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> {
    val selector = when (type) {
        PyPathSelectorType.BaselinePriorityDfs ->
            createBaselinePriorityDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselineWeightedDfs ->
            createBaselineWeightedDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityWeightedByNumberOfVirtual ->
            createBaselineWeightedByNumberOfVirtualPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityPlusTypeRatingByHintsDfs ->
            createTypeRatingByHintsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityDfs ->
            createDelayedForkByInstructionPriorityDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedDfs ->
            createDelayedForkByInstructionWeightedDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedRandomTree ->
            createDelayedForkByInstructionWeightedRandomPyPathSelector(initialState, ctx, random, newStateObserver)
    }
    selector.add(listOf(initialState))
    return selector
}

fun createBaselinePriorityDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselinePriorityActionStrategy(random),
        BaselineDelayedForkStrategy(),
        BaselineDFGraphCreation { DfsPathSelector() },
        newStateObserver
    )

fun createBaselineWeightedDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselineWeightedActionStrategy(random),
        BaselineDelayedForkStrategy(),
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
        makeBaselinePriorityActionStrategy(random),
        BaselineDelayedForkStrategy(),
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
                    val tupleOfModels = PyTupleObject(models)
                    val mocks = calculateNumberOfMocks(tupleOfModels)
                    logger.debug { "Mocks of state $it: $mocks" }
                    1.0 / max(1, 10 * mocks)
                }
            )
        },
        newStateObserver
    )

fun createDelayedForkByInstructionPriorityDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionPriorityStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation { DfsPathSelector() },
        newStateObserver
    )

fun createDelayedForkByInstructionWeightedDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation { DfsPathSelector() },
        newStateObserver
    )

fun createDelayedForkByInstructionWeightedRandomPyPathSelector(
    initialState: PyState,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            RandomTreePathSelector.fromRoot(
                initialState.pathNode,
                randomNonNegativeInt = { random.nextInt(0, it) }
            )
        },
        newStateObserver
    )

fun createTypeRatingByHintsDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselinePriorityActionStrategy(random),
        TypeRatingByNumberOfHints(),
        BaselineDFGraphCreation { DfsPathSelector() },
        newStateObserver
    )