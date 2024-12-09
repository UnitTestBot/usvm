package org.usvm.machine.utils

import org.usvm.PathNode
import org.usvm.language.PyCallable
import org.usvm.language.PyCodeObject
import org.usvm.language.PyInstruction
import org.usvm.language.extractInstructionsFromCode
import org.usvm.language.prettyPrint
import org.usvm.language.prettyRepresentation
import org.usvm.machine.PyState
import org.usvm.statistics.UDebugProfileObserver


val pyDebugProfileObserver = UDebugProfileObserver(
    statementOperations = object : UDebugProfileObserver.StatementOperations<PyInstruction, PyCallable, PyState> {
        override fun getMethodOfStatement(statement: PyInstruction) = PyCodeObject(statement.code)

        override fun getStatementIndexInMethod(statement: PyInstruction) = statement.numberInBytecode

        override fun getMethodToCallIfCallStatement(statement: PyInstruction) = null

        override fun printStatement(statement: PyInstruction) = statement.prettyRepresentation()

        override fun getAllMethodStatements(method: PyCallable): List<PyInstruction> {
            check(method is PyCodeObject) {
                "Unexpected callable: $this"
            }
            return extractInstructionsFromCode(method.codeObject)
        }

        override fun printMethodName(method: PyCallable): String {
            check(method is PyCodeObject) {
                "Unexpected callable: $this"
            }
            return method.prettyPrint()
        }

        override fun getNewStatements(state: PyState): List<PathNode<PyInstruction>> {
            val result = mutableListOf<PathNode<PyInstruction>>()
            var curNode: PathNode<PyInstruction>? = state.pathNode
            val prevNode = state.pathNodeBreakpoints.lastOrNull()
                ?: PathNode.root()

            while (curNode != null && curNode != prevNode) {
                result.add(curNode)
                curNode = curNode.parent
            }

            result.reverse()
            return result
        }

        override fun forkHappened(state: PyState, statement: PathNode<PyInstruction>): Boolean {
            return false // no tracing of forks for now
        }
    },
    profilerOptions = UDebugProfileObserver.Options(
        momentOfUpdate = UDebugProfileObserver.MomentOfUpdate.AfterStep,
        printNonVisitedStatements = true,
    ),
)
