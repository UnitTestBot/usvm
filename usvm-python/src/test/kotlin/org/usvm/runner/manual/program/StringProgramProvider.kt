package org.usvm.runner.manual.program

import org.usvm.language.PrimitivePyProgram
import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.BasicPythonTypeSystem
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem

class StringProgramProvider(
    programCode: String,
    functions: List<Pair<String, List<PythonType>>>
): ProgramProvider() {
    override val program: PyProgram =
        PrimitivePyProgram.fromString(programCode)

    override val typeSystem: PythonTypeSystem =
        BasicPythonTypeSystem()

    override val functions: List<PyUnpinnedCallable> =
        functions.map { (name, signature) ->
            PyUnpinnedCallable.constructCallableFromName(
                signature,
                name,
                null
            )
        }
}