package org.usvm.fuzzer.generator.random

import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.random.JDKRandomGenerator
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FuzzerRandomNormalDistribution(private val seed: Int, mean: Double, deviation: Double): JDKRandomGenerator(seed) {


    private val normalDistribution = NormalDistribution(this, mean, deviation)

    override fun nextInt(): Int = normalDistribution.sample().roundToInt()

    override fun nextLong(): Long =
        normalDistribution.sample().roundToLong()

    override fun nextFloat(): Float = normalDistribution.sample().toFloat()

}