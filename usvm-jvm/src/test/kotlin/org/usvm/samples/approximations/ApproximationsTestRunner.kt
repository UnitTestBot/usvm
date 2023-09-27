package org.usvm.samples.approximations

import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.samplesWithApproximationsKey

abstract class ApproximationsTestRunner : JavaMethodTestRunner() {
    override val jacodbCpKey: String
        get() = samplesWithApproximationsKey
}
