package org.usvm

import org.usvm.statistics.ApplicationGraph

interface ApplicationBlockGraph<Method, BasicBlock, Statement> : ApplicationGraph<Method, BasicBlock> {
    fun blockOf(stmt: Statement): BasicBlock
    fun instructions(block: BasicBlock): Sequence<Statement>
    fun blocks(): Sequence<BasicBlock>
}
