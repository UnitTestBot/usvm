package org.usvm.ts.pbt.hybrid

internal data class ResidualShardPlan<T>(
    val id: TargetShardId,
    val targets: List<T>,
) {
    init {
        require(targets.isNotEmpty()) { "residual shard plan must not be empty" }
    }
}

internal data class ResidualShardExecution<T, R>(
    val id: TargetShardId,
    val plannedTargets: List<T>,
    val replayPrunedTargets: List<T>,
    val activeTargets: List<T>,
    val launchResult: R?,
) {
    val launched: Boolean get() = launchResult != null
}

/**
 * Re-reads concrete replay coverage immediately before every shard. A fully covered shard is
 * represented in the result but never reaches [launch], which makes machine-run accounting exact.
 */
internal fun <T, R> executeResidualShards(
    shards: List<ResidualShardPlan<T>>,
    replayPruneBetweenShards: Boolean,
    targetId: (T) -> String,
    isReplayCovered: (T) -> Boolean,
    launch: (TargetShardId, List<T>) -> R,
): List<ResidualShardExecution<T, R>> {
    val shardIds = shards.map { it.id.value }
    require(shardIds.distinct().size == shardIds.size) { "duplicate residual shard IDs" }
    val targetIds = shards.flatMap(ResidualShardPlan<T>::targets).map(targetId)
    require(targetIds.distinct().size == targetIds.size) { "duplicate targets across residual shards" }

    return shards.map { shard ->
        val replayPruned = if (replayPruneBetweenShards) {
            shard.targets.filter(isReplayCovered)
        } else {
            emptyList()
        }
        val active = if (replayPruned.isEmpty()) {
            shard.targets
        } else {
            val prunedIds = replayPruned.mapTo(hashSetOf(), targetId)
            shard.targets.filterNot { targetId(it) in prunedIds }
        }
        val result = active.takeIf(List<T>::isNotEmpty)?.let { launch(shard.id, it) }
        ResidualShardExecution(
            id = shard.id,
            plannedTargets = shard.targets,
            replayPrunedTargets = replayPruned,
            activeTargets = active,
            launchResult = result,
        )
    }
}
