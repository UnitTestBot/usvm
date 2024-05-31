package org.usvm.runner.manual.program

import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.runner.SamplesBuild

class SampleProgramProvider(
    declarations: List<Pair<Pair<String, String>, List<PythonType>>>,
) : ProgramProvider {
    override val program = SamplesBuild.program

    override val typeSystem =
        PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)

    override val functions: List<PyUnpinnedCallable> =
        declarations.map { (name, sig) ->
            val (module, shortName) = name
            PyUnpinnedCallable.constructCallableFromName(sig, shortName, module)
        }
}
