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
    Test,
}

enum class Algorithm {
    BFS,
    ForkDepthRandom,
}

enum class GraphUpdate {
    Once,
    TestGeneration,
}

object MLConfig {
    var gameEnvPath = "../Game_env"
    var dataPath = "../Data"
    var defaultAlgorithm = Algorithm.BFS
    var postprocessing = Postprocessing.Argmax
    var mode = Mode.Both
    var logFeatures = true
    var shuffleTests = true
    var discounts = listOf(1.0f, 0.998f, 0.99f)
    var inputShape = listOf<Long>(1, -1, 77)
    var maxAttentionLength = -1
    var useGnn = true
    var dataConsumption = 100.0f
    var hardTimeLimit = 30000 // in ms
    var solverTimeLimit = 10000 // in ms
    var maxConcurrency = 64
    var graphUpdate = GraphUpdate.Once
    var logGraphFeatures = false
    var gnnFeaturesCount = 8
    var useRnn = true
    var rnnStateShape = listOf<Long>(4, 1, 512)
    var rnnFeaturesCount = 33
    var inputJars = mapOf(
        Pair("../Game_env/jars/usvm-jvm-new.jar", listOf("org.usvm.samples", "com.thealgorithms"))
    ) // path to jar file -> list of package names
}