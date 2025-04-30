package org.usvm.service

import manager.ManagerClient
import manager.SceneRequest

fun main() {
    val manager: ManagerClient = grpcClient().create()
    val scene = manager.GetScene().executeBlocking(SceneRequest())
    println("scene = $scene")
}
