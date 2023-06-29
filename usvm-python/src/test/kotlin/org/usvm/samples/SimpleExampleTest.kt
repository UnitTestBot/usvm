package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.PythonCallable
import org.usvm.language.PythonInt
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleExampleTest : PythonTestRunner("/samples/SimpleExample.py") {
    private val functionF = PythonCallable.constructCallableFromName(List(3) { PythonInt }, "f")
    @Test
    fun testF() {
        checkReprs3(
            functionF,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(10) { index ->
                { _, _, _, res -> res == index.toString() }
            }
        )
    }
}