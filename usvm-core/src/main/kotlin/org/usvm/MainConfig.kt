package org.usvm

enum class Postprocessing {
    Argmax,
    Softmax,
    None
}

enum class Mode {
    Calculation,
    Aggregation,
    Both,
}

object MainConfig {
    var samplesPath: String = "../Game_env/usvm-jvm/src/samples/java"
    var gameEnvPath: String = "../Game_env"
    var dataPath: String = "../Data"
    var postprocessing = Postprocessing.Argmax
    var mode = Mode.Both
    var inputShape = listOf<Long>(1, -1, 34)
    var maxAttentionLength = -1
    var dataConsumption = 100.0f
    var hardTimeLimit = 30000 // in ms
}