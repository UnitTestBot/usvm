package org.usvm

enum class Postprocessing {
    Argmax,
    Softmax,
    None,
}

enum class Mode {
    Calculation,
    Aggregation,
    Both,
}

enum class Algorithm {
    BFS,
    ForkDepthRandom,
}

enum class GraphUpdate {
    Once,
    TestGeneration,
}

object MainConfig {
    var samplesPath = "../Game_env/usvm-jvm/src/samples/java"
    var gameEnvPath = "../Game_env"
    var dataPath = "../Data"
    var defaultAlgorithm = Algorithm.BFS
    var postprocessing = Postprocessing.Argmax
    var mode = Mode.Both
    var inputShape = listOf<Long>(1, -1, 34)
    var maxAttentionLength = -1
    var useGnn = true
    var dataConsumption = 100.0f
    var hardTimeLimit = 30000 // in ms
    var solverTimeLimit = 10000 // in ms
    var maxConcurrency = 64
    var graphUpdate = GraphUpdate.Once
    var logGraphFeatures = false
    var gnnFeaturesCount = 8
}