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

data class MLConfig (
    val gameEnvPath: String = "../Game_env",
    val dataPath: String = "../Data",
    val defaultAlgorithm: Algorithm = Algorithm.BFS,
    val postprocessing: Postprocessing = Postprocessing.Argmax,
    val mode: Mode = Mode.Both,
    val logFeatures: Boolean = true,
    val shuffleTests: Boolean = true,
    val discounts: List<Float> = listOf(1.0f, 0.998f, 0.99f),
    val inputShape: List<Long> = listOf(1, -1, 77),
    val maxAttentionLength: Int = -1,
    val useGnn: Boolean = true,
    val dataConsumption: Float = 100.0f,
    val hardTimeLimit: Int = 30000, // in ms
    val solverTimeLimit: Int = 10000, // in ms
    val maxConcurrency: Int = 64,
    val graphUpdate: GraphUpdate = GraphUpdate.Once,
    val logGraphFeatures: Boolean = false,
    val gnnFeaturesCount: Int = 8,
    val useRnn: Boolean = true,
    val rnnStateShape: List<Long> = listOf(4, 1, 512),
    val rnnFeaturesCount: Int = 33,
    val inputJars: Map<String, List<String>> = mapOf(
        Pair("../Game_env/jars/usvm-jvm-new.jar", listOf("org.usvm.samples", "com.thealgorithms"))
    ) // path to jar file -> list of package names
)
