package org.usvm.machine.ps

import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.ps.strategies.impls.*
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.RandomTreePathSelector
import org.usvm.python.model.PyTupleObject
import org.usvm.python.model.calculateNumberOfMocks
import org.usvm.python.ps.PyPathSelectorType
import kotlin.math.log
import kotlin.math.max
import kotlin.random.Random


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

        PyPathSelectorType.BaselinePriorityNumberOfVirtualDfs ->
            createBaselinePriorityNumberOfVirtualDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityNumberOfInstructionsDfs ->
            createBaselinePriorityNumberOfInstructionsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityPlusTypeRatingByHintsDfs ->
            createTypeRatingByHintsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityDfs ->
            createDelayedForkByInstructionPriorityDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedDfs ->
            createDelayedForkByInstructionWeightedDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedRandomTree ->
            createDelayedForkByInstructionWeightedRandomTreePyPathSelector(initialState, ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfVirtualDfs ->
            createDelayedForkByInstructionPriorityNumberOfVirtualDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedNumberOfVirtualDfs ->
            createDelayedForkByInstructionWeightedNumberOfVirtualDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfInstructionsDfs ->
            createDelayedForkByInstructionPriorityNumberOfInstructionsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedNumberOfInstructionsDfs ->
            createDelayedForkByInstructionWeightedNumberOfInstructionDfsPyPathSelector(ctx, random, newStateObserver)
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

fun createBaselinePriorityNumberOfVirtualDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselinePriorityActionStrategy(random),
        BaselineDelayedForkStrategy(),
        BaselineDFGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfVirtual, ::mockWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createBaselinePriorityNumberOfInstructionsDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselinePriorityActionStrategy(random),
        BaselineDelayedForkStrategy(),
        BaselineDFGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfInstructions, ::instructionWeight) {
                DfsPathSelector()
            }
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

fun createDelayedForkByInstructionWeightedRandomTreePyPathSelector(
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

fun createDelayedForkByInstructionPriorityNumberOfVirtualDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionPriorityStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfVirtual, ::mockWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createDelayedForkByInstructionPriorityNumberOfInstructionsDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionPriorityStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfInstructions, ::instructionWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createDelayedForkByInstructionWeightedNumberOfVirtualDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfVirtual, ::mockWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createDelayedForkByInstructionWeightedNumberOfInstructionDfsPyPathSelector(
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(random, ::calculateNumberOfInstructions, ::instructionWeight) {
                DfsPathSelector()
            }
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


private fun calculateNumberOfVirtual(state: PyState): Int =
   runCatching {
       val modelHolder = PyModelHolder(state.pyModel)
       val builder = PyObjectModelBuilder(state, modelHolder)
       val models = state.inputSymbols.map { symbol ->
           val interpreted = interpretSymbolicPythonObject(modelHolder, state.memory, symbol)
           builder.convert(interpreted)
       }
       val tupleOfModels = PyTupleObject(models)
       calculateNumberOfMocks(tupleOfModels)
   }.getOrDefault(5)


private fun mockWeight(mocks: Int) = 1.0 / max(1, mocks + 1)

private fun calculateNumberOfInstructions(state: PyState) = state.uniqueInstructions.size

private fun instructionWeight(instructions: Int) = log(instructions + 8.0, 2.0)