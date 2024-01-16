package org.usvm.fuzzer.strategy.fitness

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.fuzzing.FuzzingStorage
import org.usvm.fuzzer.seed.Seed

interface TestFitness {
    fun getFitness(seed: Seed, targetInsts: List<JcInst>): Double
}