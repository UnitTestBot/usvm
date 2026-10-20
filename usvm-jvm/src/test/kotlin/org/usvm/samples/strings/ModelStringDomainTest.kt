package org.usvm.samples.strings

import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ModelStringDomainTest : ApproximationsTestRunner() {
    private var enforceBound = true

    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?,
    ): JcMachine = JcMachine(
        cp = cp,
        options = options,
        jcMachineOptions = JcMachineOptions(enforceModelStringLengthBound = enforceBound),
        interpreterObserver = interpreterObserver,
    )

    @Test
    fun `default preserves the explicit legacy model bound`() {
        enforceBound = true

        checkDiscoveredProperties(
            ModelStringDomain::lengthBoundary,
            eq(count = 2),
            { value, result -> value == null && result == -1 },
            { value, result -> value != null && value.length <= 50 && result == 0 },
        )
    }

    @Test
    fun `disabling only the model bound admits symbolic strings longer than fifty`() {
        enforceBound = false

        checkDiscoveredProperties(
            ModelStringDomain::lengthBoundary,
            eq(count = 3),
            { value, result -> value == null && result == -1 },
            { value, result -> value != null && value.length == 51 && result == 1 },
            { value, result -> value != null && value.length != 51 && result == 0 },
        )
    }

    @Test
    fun `user symbolic assumptions are still enforced with the model bound disabled`() {
        enforceBound = false

        checkDiscoveredProperties(
            ModelStringDomain::userAssumption,
            eq(count = 2),
            { value, result -> value == null && result == -1 },
            { value, result -> value != null && value.length <= 2 && result == 1 },
            invariants = arrayOf({ value, _ -> value == null || value.length <= 2 }),
        )
    }

    @Test
    fun `character model retains meaningful data past the legacy boundary`() {
        enforceBound = false

        checkDiscoveredProperties(
            ModelStringDomain::characterBeyondBoundary,
            ignoreNumberOfAnalysisResults,
            { value, result -> value == null && result == -1 },
            { value, result -> value != null && value.length != 51 && result == 0 },
            { value, result -> value != null && value.length == 51 && value[50] == 'A' && result == 1 },
            { value, result -> value != null && value.length == 51 && value[50] != 'A' && result == 2 },
            invariants = arrayOf({ value, result ->
                val expected = when {
                    value == null -> -1
                    value.length != 51 -> 0
                    value[50] == 'A' -> 1
                    else -> 2
                }
                result == expected
            }),
        )
    }
}
