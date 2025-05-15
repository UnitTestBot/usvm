package org.usvm.service

import manager2.ManagerClient
import manager2.SceneRequest

fun main() {
    val manager: ManagerClient = grpcClient().create()
    val scene = manager.GetScene().executeBlocking(SceneRequest())
    println("scene = $scene")
}
