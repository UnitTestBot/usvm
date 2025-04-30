package org.usvm.service

import manager.ManagerClient
import manager.SceneRequest
import mu.KotlinLogging
import kotlin.io.path.pathString
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

fun main() {
    val time = measureTime {
        val client: ManagerClient = grpcClient(50051).create()
        // val path = getResourcePath("/projects/Demo_Calc/source/entry")
        val path = getResourcePath("/projects/Launcher/source")
        logger.info { "Requesting scene for '$path'..." }
        val (response, timeRequest) = measureTimedValue {
            client.GetScene().executeBlocking(SceneRequest(path.pathString))
        }
        val scene = response.scene!!
        logger.info {
            "Got scene in %.1fs with ${
                scene.files.size
            } files, ${
                scene.files.flatMap { it.classes }.size
            } classes, ${
                scene.files.flatMap { it.classes }.flatMap { it.methods }.size
            } methods".format(timeRequest.toDouble(DurationUnit.SECONDS))
        }
    }
    logger.info { "All done in %.2f seconds.".format(time.toDouble(DurationUnit.SECONDS)) }
}
