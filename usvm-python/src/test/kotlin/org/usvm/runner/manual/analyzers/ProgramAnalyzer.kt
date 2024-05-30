package org.usvm.runner.manual.analyzers

import org.usvm.runner.manual.program.ProgramProvider

abstract class ProgramAnalyzer {
    abstract fun run(provider: ProgramProvider)
}