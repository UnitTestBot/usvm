package org.usvm.fuzzer.mutation

import org.jacodb.api.JcField
import org.usvm.fuzzer.seed.Seed

data class MutationInfo(
    val mutatedArg: Seed.ArgumentDescriptor?,
    val mutatedField: JcField?,
)