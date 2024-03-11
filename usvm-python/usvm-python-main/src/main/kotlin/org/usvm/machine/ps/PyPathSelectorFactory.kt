package org.usvm.machine.ps

import org.usvm.PathNode
import org.usvm.language.PyInstruction
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
    val initialNode = initialState.pathNode
    val selector = when (type) {
        PyPathSelectorType.BaselinePriorityDfs ->
            createBaselinePriorityDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselineWeightedDfs ->
            createBaselineWeightedDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityNumberOfVirtualDfs ->
            createBaselinePriorityNumberOfVirtualDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselineWeightedNumberOfVirtualRandomTree ->
            createBaselineWeightedNumberOfVirtualRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityNumberOfInstructionsDfs ->
            createBaselinePriorityNumberOfInstructionsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityNumberOfInstructionsRandomTree ->
            createBaselinePriorityNumberOfInstructionsRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

        PyPathSelectorType.BaselinePriorityPlusTypeRatingByHintsDfs ->
            createTypeRatingByHintsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedDfs ->
            createDelayedForkByInstructionWeightedDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedRandomTree ->
            createDelayedForkByInstructionWeightedRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfVirtualDfs ->
            createDelayedForkByInstructionPriorityNumberOfVirtualDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedNumberOfVirtualRandomTree ->
            createDelayedForkByInstructionWeightedNumberOfVirtualRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfInstructionsDfs ->
            createDelayedForkByInstructionPriorityNumberOfInstructionsDfsPyPathSelector(ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfInstructionsRandomTree ->
            createDelayedForkByInstructionPriorityNumberOfInstructionsRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

        PyPathSelectorType.DelayedForkByInstructionWeightedNumberOfInstructionsRandomTree ->
            createDelayedForkByInstructionWeightedNumberOfInstructionsRandomTreePyPathSelector(initialNode, ctx, random, newStateObserver)

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
            WeightedPyPathSelector(random, proportionalToSelectorSize = true, ::calculateNumberOfVirtual, ::mockWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createBaselineWeightedNumberOfVirtualRandomTreePyPathSelector(
    initialNode: PathNode<PyInstruction>,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselineWeightedActionStrategy(random),
        BaselineDelayedForkStrategy(),
        BaselineDFGraphCreation {
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = true,
                ::calculateNumberOfVirtual,
                ::mockWeight
            ) {
                RandomTreePathSelector.fromRoot(
                    initialNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
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
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = false,
                ::calculateNumberOfInstructions,
                ::instructionWeight
            ) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createBaselinePriorityNumberOfInstructionsRandomTreePyPathSelector(
    initialNode: PathNode<PyInstruction>,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeBaselinePriorityActionStrategy(random),
        BaselineDelayedForkStrategy(),
        BaselineDFGraphCreation {
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = false,
                ::calculateNumberOfInstructions,
                ::instructionWeight
            ) {
                RandomTreePathSelector.fromRoot(
                    initialNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
            }
        },
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
    initialNode: PathNode<PyInstruction>,
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
                initialNode,
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
            WeightedPyPathSelector(random, proportionalToSelectorSize = true, ::calculateNumberOfVirtual, ::mockWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )


fun createDelayedForkByInstructionWeightedNumberOfVirtualRandomTreePyPathSelector(
    initialNode: PathNode<PyInstruction>,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = true,
                ::calculateNumberOfVirtual,
                ::mockWeight
            ) {
                RandomTreePathSelector.fromRoot(
                    initialNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
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
            WeightedPyPathSelector(random, proportionalToSelectorSize = false, ::calculateNumberOfInstructions, ::instructionWeight) {
                DfsPathSelector()
            }
        },
        newStateObserver
    )

fun createDelayedForkByInstructionPriorityNumberOfInstructionsRandomTreePyPathSelector(
    initialNode: PathNode<PyInstruction>,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionPriorityStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = false,
                ::calculateNumberOfInstructions,
                ::instructionWeight
            ) {
                RandomTreePathSelector.fromRoot(
                    initialNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
            }
        },
        newStateObserver
    )

fun createDelayedForkByInstructionWeightedNumberOfInstructionsRandomTreePyPathSelector(
    initialNode: PathNode<PyInstruction>,
    ctx: PyContext,
    random: Random,
    newStateObserver: NewStateObserver
): PyVirtualPathSelector<*, *> =
    PyVirtualPathSelector(
        ctx,
        makeDelayedForkByInstructionWeightedStrategy(random),
        BaselineDelayedForkStrategy(),
        DelayedForkByInstructionGraphCreation {
            WeightedPyPathSelector(
                random,
                proportionalToSelectorSize = false,
                ::calculateNumberOfInstructions,
                ::instructionWeight
            ) {
                RandomTreePathSelector.fromRoot(
                    initialNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
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