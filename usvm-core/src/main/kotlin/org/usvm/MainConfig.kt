package org.usvm

enum class Algorithm {
    TD,
    PPO
}

object MainConfig {
    var samplesPath: String = "../Game_env/usvm-jvm/src/samples/java"
    var gameEnvPath: String = "../Game_env"
    var dataPath: String = "../Data"
    var algorithm = Algorithm.TD
}