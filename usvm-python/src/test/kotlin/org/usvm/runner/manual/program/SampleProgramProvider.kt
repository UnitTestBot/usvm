package org.usvm.runner.manual.program

import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.runner.SamplesBuild

class SampleProgramProvider(
    moduleName: String,
    functionName: String,
    signature: (PythonTypeSystemWithMypyInfo) -> List<PythonType>,
) : ProgramProvider {
    override val program = SamplesBuild.program

    override val typeSystem =
        PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)

    override val functions: List<PyUnpinnedCallable> =
        listOf(
            PyUnpinnedCallable.constructCallableFromName(
                signature(typeSystem),
                functionName,
                moduleName,
            )
        )
}
