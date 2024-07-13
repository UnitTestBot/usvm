package org.usvm.runner.manual

import mu.KLogging
import org.usvm.runner.manual.analyzers.OrdinaryAnalyzer
import org.usvm.runner.manual.program.sampleFunction

/**
 * This should be run with task `manualTestDebug` or `manualTestDebugNoLogs`
 * */
fun main() {
    /**
     * See:
     *  - [org.usvm.runner.manual.program.sampleStringFunction]
     *  - [org.usvm.runner.manual.program.sampleFunction]
     *  - [org.usvm.runner.manual.program.LocalProgramProvider]
     * */
    val program = sampleFunction

    /**
     * See:
     *  - [org.usvm.runner.manual.analyzers.OrdinaryAnalyzer]
     *  - [org.usvm.runner.manual.analyzers.ConcolicAndConcreteChecker]
     * */
    val analyzer = OrdinaryAnalyzer

    analyzer.run(program)
}

val manualTestLogger = object : KLogging() {}.logger
