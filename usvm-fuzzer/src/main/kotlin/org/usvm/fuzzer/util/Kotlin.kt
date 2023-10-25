package org.usvm.fuzzer.util

import kotlin.random.Random

fun Random.getTrueWithProb(prob: Int) = nextInt(101) in 0..prob