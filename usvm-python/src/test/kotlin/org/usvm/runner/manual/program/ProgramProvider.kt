package org.usvm.runner.manual.program

import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonTypeSystem

interface ProgramProvider {
    val program: PyProgram
    val typeSystem: PythonTypeSystem
    val functions: List<PyUnpinnedCallable>
}
