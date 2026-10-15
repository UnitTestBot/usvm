# External coverage goals

`CoverageTargets<Target>` supplies the currently uncovered goals and a revision that changes whenever that set changes.
The normal `CoverageStatistics` implements this contract for statements. `ExternalCoverageTargets` lets a caller
register goals and report confirmed coverage between analysis steps.

The standard factory accepts this source independently of the statistics used for reporting and stopping:

```kotlin
val coverage = ExternalCoverageTargets(targets = knownStatements)
coverage.markCovered(targets = statementsCoveredByConcreteTests)

val selector = createPathSelector(
    initialState = initialState,
    options = options.copy(pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED)),
    applicationGraph = graph,
    cfgStatisticsFactory = { cfgStatistics },
    coverageTargetsFactory = { coverage },
)

// The analysis can continue with the same queue after the caller confirms more coverage.
coverage.markCovered(targets = newlyConfirmedStatements)
```

Both `CLOSEST_TO_UNCOVERED` variants use cached CFG distances in the active call stack. Existing entry-point fairness,
exception propagation, loop bounds and selector composition remain controlled by `UMachineOptions`.
External goals do not change reported coverage percentages or coverage-based stop conditions.

For targets that are not statements, use the same weighted queue with a caller-supplied distance:

```kotlin
data class Edge<Statement>(val from: Statement, val to: Statement)

val coverage = ExternalCoverageTargets(targets = knownEdges)
coverage.markCovered(targets = edgesCoveredByConcreteTests)

val selector = createCoverageGuidedPathSelector(
    coverageTargets = coverage,
    distance = ::distanceFromStateToEdge,
)
```

The distance function owns the meaning of each target and returns `UInt.MAX_VALUE` when it is unreachable.
An edge must retain both endpoints: reaching its successor through a different predecessor does not cover that edge.
The core does not turn an edge into a statement or infer coverage from the distance function.
Language-specific code can reuse the existing CFG and interprocedural distance calculators in this function.

Updates are applied on the analysis thread, between machine steps. The next `peek()` refreshes all queued weights
after a revision change; repeated peeks with unchanged evidence reuse the weights. No listener keeps the selector
attached to the external source after the analysis ends. Draining stopped work does not calculate new distances.

`markCovered` ignores unknown goals, and repeated coverage reports do not change the revision. Use `addTargets`
to register newly identified goals; re-registering a covered goal does not make it uncovered again. Missing records
are not automatically registered as uncovered goals.

An empty goal set retains every queued state. Covered prefixes remain traversable, and coverage alone does not
establish a property on other inputs. Property search must keep its own stop conditions and exploration policy.
Runtime admission, source identity and concrete replay confirmation belong to the caller; this core API accepts
its coverage decisions without interpreting them.
