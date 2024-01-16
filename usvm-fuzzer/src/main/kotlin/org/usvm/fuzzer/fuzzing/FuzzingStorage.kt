package org.usvm.fuzzer.fuzzing

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.seed.Seed

class FuzzingStorage(
    targetMethod: JcMethod,
) {

    val coveredInstructions: MutableSet<JcInst> = mutableSetOf()
    val nonCoveredBranches: MutableSet<JcInst> = mutableSetOf()
    //Will fill after initial seed generation
    val targetBranches: MutableSet<JcInst> = mutableSetOf()

    val testSuite: MutableList<Seed> = mutableListOf()

    init {
        nonCoveredBranches.addAll(targetMethod.instList.filterIsInstance<JcIfInst>())
    }


}