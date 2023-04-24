package org.usvm.programs

import org.usvm.language.*
import org.usvm.language.builders.*

object DfsProgram : ProgramDecl() {
    object Node : StructDecl() {
        val x by IntType
        val nodes by ArrayType(IntType)
    }

    object Graph : StructDecl() {
        val n by IntType
        val g by ArrayType(Node.type)
    }

    val dfs: Method<IntType> by method(
        Graph.type,
        IntType,
        ArrayType(BooleanType),
        returnType = IntType
    ) { g, u, used ->
        branch(used[u]) {
            ret(0.expr)
        }
        used[u] = true.expr

        var sum by g[Graph.g][u][Node.x]
        var idx by 0.expr
        loop(idx lt g[Graph.g][u][Node.nodes].size) {
            val v by g[Graph.g][u][Node.nodes][idx]
            sum += dfs(g, v, used)
            idx += 1.expr
        }

        ret(sum)
    }

    val calcSumLoop: Method<BooleanType> by method(
        ArrayType(IntType),
        returnType = BooleanType
    ) { arr ->
        val n by arr.size
        branch(n ge 10.expr) { // TODO: fix array soft constraints and path selector
            ret(true.expr)
        }
        val graph by ArrayType(Node.type)(size = n)
        var expectedSum by 0.expr

        var idx by 0.expr
        var same by true.expr
        loop(idx lt n) {
            val nodes by ArrayType(IntType)(idx + 1.expr)
            branch(idx + 1.expr eq n) {
                nodes[0.expr] = 0.expr
            }
            graph[idx] = Node.type(Node.x to arr[idx], Node.nodes to nodes)

            same = same and (arr[idx] eq arr[0.expr])

            expectedSum += arr[idx]
            idx += 1.expr
        }


        val g by Graph.type(Graph.g to graph, Graph.n to n)
        val used by ArrayType(BooleanType)(size = n)
        val sum by dfs(g, 0.expr, used)

        branch(same and (n eq 7.expr) and (sum % 7.expr eq 0.expr).not()) {
            ret(false.expr)
        }
        ret(true.expr)
    }

}