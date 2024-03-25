package org.usvm.samples

import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.parser.ByteCodeParser
import org.jacodb.panda.dynamic.parser.IRParser
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.PandaExecutionResult
import org.usvm.machine.PandaMachine
import org.usvm.test.util.TestRunner
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias MethodName = String
private typealias Coverage = Int
private typealias PathString = String

open class PandaMethodTestRunner : TestRunner<PandaExecutionResult, Pair<PathString, MethodName>, PandaType?, Coverage>() {
    override val typeTransformer: (Any?) -> PandaType
        get() = { _ -> PandaAnyType } // TODO("Not yet implemented")

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override val checkType: (PandaType?, PandaType?) -> Boolean
        get() = { expected, actual -> true } // TODO("Not yet implemented")

    @Suppress("UNUSED_ANONYMOUS_PARAMETER", "UNUSED_VARIABLE")
    override val runner: (Pair<PathString, MethodName>, UMachineOptions) -> List<PandaExecutionResult>
        get() = { id, options ->
            val filePath = "/samples/" + id.first + ".abc"
            val bcParser = javaClass.getResource(filePath)
                ?.path
                ?.let { FileInputStream(it).readBytes() }
                ?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
                ?.let { ByteCodeParser(it) }
                ?.also { it.parseABC() }

            // TODO Automatic parser?????
            val jsonWithoutExtension = "/samples/${id.first}.json"
            val sampleFilePath = javaClass.getResource(jsonWithoutExtension)?.path ?: ""
            val parser = IRParser(sampleFilePath, bcParser!!)
            val project = parser.getProject()

            // TODO class name??????
            val method = project.findMethodOrNull(id.second, "L_GLOBAL") ?: error("TODO")
            val machine = PandaMachine(project, options)

            machine.analyze(listOf(method))

            TODO()
        }

    override val coverageRunner: (List<PandaExecutionResult>) -> Coverage
        get() = { _ -> 0 } // TODO("Not yet implemented")

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}