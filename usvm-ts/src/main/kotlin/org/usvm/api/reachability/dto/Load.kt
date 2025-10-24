package org.usvm.api.reachability.dto

import mu.KotlinLogging
import org.usvm.api.reachability.TargetTrace
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun loadTraces(path: Path): List<TargetTrace> {
    logger.debug { "Loading targets from $path" }
    val container = TargetsContainerDto.from(path)
    logger.debug { "Parsed targets from $path" }
    val traces = extractTargetTraces(container)
    logger.debug { "Extracted ${traces.size} traces from $path" }
    return traces
}
