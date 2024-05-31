package org.usvm.runner.manual.analyzers

import org.usvm.runner.manual.program.ProgramProvider

interface ProgramAnalyzer {
    fun run(provider: ProgramProvider)
}
