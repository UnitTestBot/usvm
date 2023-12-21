package org.usvm.fuzzer.generator

import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

object GeneratorSettings {

    val generationMode = GenerationMode.SAFE
    const val mocksEnabled = true
    const val mockGenerationProbability = 0

    //Time for object generation
    val softTimeoutForObjectGeneration = 2.seconds
    val hardTimeoutForObjectGeneration = 3.seconds

    //Common settings
    const val maxDepthOfObjectGeneration = 5

    //Random
    private const val RANDOM_SEED = 42
//    private val random = Random(RANDOM_SEED)


    //Bounds for primitives generation
    const val minByte: Byte = -100
    const val maxByte: Byte = 100
    val minShort: Int = -100
    val maxShort: Int = 100
    val minChar: Int = Character.MIN_VALUE.code
    val maxChar: Int = Character.MAX_VALUE.code
    val minInt: Int = -100
    val maxInt: Int = 100
    val minLong: Long = -100
    val maxLong: Long = 100
    val minFloat: Double = -100.0
    val maxFloat: Double = 100.0
    val minDouble: Double = -100.0
    val maxDouble: Double = 100.0
    val minStringLength: Int = 0
    val maxStringLength: Int = 10
    val minCollectionSize: Int = 0
    val maxCollectionSize: Int = 5

    //All alphabetical symbols and special characters
    val stringAvailableSymbols = ' '..'~'
}

enum class GenerationMode {
    //Only public API
    SAFE,

    //Reflection, unsafe
    UNSAFE
}