package org.usvm.fuzzer.generator.random

import java.util.Random
import java.util.concurrent.ThreadLocalRandom

fun Random.nextInt(lowerBound: Int, upperBound: Int): Int =
    ThreadLocalRandom.current().nextInt(lowerBound, upperBound)

fun Random.nextLong(lowerBound: Long, upperBound: Long): Long =
    ThreadLocalRandom.current().nextLong(lowerBound, upperBound)

fun Random.nextDouble(lowerBound: Double, upperBound: Double): Double =
    ThreadLocalRandom.current().nextDouble(lowerBound, upperBound)

fun Random.getTrueWithProb(prob: Int) = nextInt(101) in 0..prob
