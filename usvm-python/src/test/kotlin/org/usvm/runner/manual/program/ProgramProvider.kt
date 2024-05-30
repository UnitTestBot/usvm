package org.usvm.runner.manual.program

import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonTypeSystem

abstract class ProgramProvider {
    abstract val program: PyProgram
    abstract val typeSystem: PythonTypeSystem
    abstract val functions: List<PyUnpinnedCallable>
}