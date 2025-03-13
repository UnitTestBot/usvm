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
