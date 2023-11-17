package org.usvm.samples.approximations

import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.samplesWithApproximationsKey
import org.usvm.util.loadClasspathFromEnv
import java.io.File

abstract class ApproximationsTestRunner : JavaMethodTestRunner() {
    override val jacodbCpKey: String
        get() = samplesWithApproximationsKey

    override val classpath: List<File>
        get() = samplesClasspathWithApproximations

    companion object {
        private const val ENV_TEST_SAMPLES_WITH_APPROXIMATIONS = "usvm.jvm.test.samples.approximations"

        val samplesClasspathWithApproximations by lazy {
            loadClasspathFromEnv(ENV_TEST_SAMPLES_WITH_APPROXIMATIONS)
        }
    }
}
