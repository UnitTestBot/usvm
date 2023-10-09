package org.usvm

data class UMachineStats(
    var total: Long,
    var current: Long,
    var terminated: Long,
    var unsat: Long,
    var unknown: Long,
    var blacklisted: Long,
    var dead: Long,
)
