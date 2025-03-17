package org.usvm.model

data class TsBasicBlock(
    val id: Int,
    val statements: List<TsStmt>,
) {
    init {
        require(statements.isNotEmpty()) { "Empty block $id" }
    }
}

class TsBlockCfg(
    val blocks: List<TsBasicBlock>,
    val successors: Map<Int, List<Int>>, // for 'if-stmt' block, successors are (true, false) branches
) {
    init {
        for ((i, block) in blocks.withIndex()) {
            require(block.id == i) { "Block id ${block.id} mismatch index $i" }
        }
        for ((id, successorIds) in successors) {
            require(id in 0..blocks.size) { "Block id $id is out of bounds" }
            for (s in successorIds) {
                require(s in 0..blocks.size) { "Successor $s is out of bounds" }
            }
        }
    }

    val stmts: List<TsStmt> by lazy {
        val queue = ArrayDeque<TsBasicBlock>()
        val visited: MutableSet<TsBasicBlock> = hashSetOf()
        val result = mutableListOf<TsStmt>()

        if (blocks.isNotEmpty()) {
            queue += blocks[0]
        }

        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            if (visited.add(block)) {
                result += block.statements
                for (s in successors[block.id].orEmpty()) {
                    queue += blocks[s]
                }
            }
        }

        result
    }

    private val stmtToBlock: MutableMap<TsStmt, TsBasicBlock> = hashMapOf()
    private fun getBlock(stmt: TsStmt): TsBasicBlock =
        stmtToBlock.getOrPut(stmt) {
            val block = blocks.find { it.statements.contains(stmt) }
                ?: error("Cannot find block for statement $stmt")
            block
        }

    fun successors(stmt: TsStmt): List<TsStmt> {
        val block = getBlock(stmt)
        val i = block.statements.indexOf(stmt)
        if (i == -1) {
            error("Cannot find statement $stmt in block ${block.id}")
        }
        if (i == block.statements.lastIndex) {
            val successorIds = successors[block.id]
                ?: error("Cannot find successors for block ${block.id}")
            return successorIds.map { s ->
                val successor = blocks[s]
                if (successor.statements.isNotEmpty()) {
                    successor.statements[0]
                } else {
                    TsNopStmt(TsInstLocation(stmt.location.method, -1))
                }
            }
        } else {
            val next = block.statements[i + 1]
            return listOf(next)
        }
    }

    companion object {
        val EMPTY: TsBlockCfg by lazy {
            TsBlockCfg(emptyList(), emptyMap())
        }
    }
}

private fun TsStmt.toDotLabel() = when (this) {
    is TsNopStmt -> "nop"
    is TsAssignStmt -> "$lhv := $rhv"
    is TsReturnStmt -> "return $returnValue"
    is TsIfStmt -> "if ($condition)"
    is TsCallStmt -> "call $expr"
    is TsRawStmt -> "raw $kind"
    else -> error("Unsupported statement: $this")
}

fun TsBlockCfg.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    // Nodes
    for (block in blocks) {
        val s = block.statements.joinToString("") { it.toDotLabel() + "\\l" }
        lines += "  ${block.id} [label=\"Block #${block.id}\\n${s}\"]"
    }

    // Edges
    for (block in blocks) {
        val succs = successors[block.id] ?: error("No successors for block ${block.id}")
        if (succs.isEmpty()) continue
        if (succs.size == 1) {
            lines += "  ${block.id} -> ${succs.single()}"
        } else {
            check(succs.size == 2)
            val (falseBranch, trueBranch) = succs // Note the order of successors: (false, true) branches
            lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
            lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}
