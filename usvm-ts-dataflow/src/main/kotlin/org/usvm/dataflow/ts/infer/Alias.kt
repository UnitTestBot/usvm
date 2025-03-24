package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsConstant
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ts.infer.MethodAliasInfoImpl.Allocation

interface StmtAliasInfo {
    fun getAliases(path: AccessPath): Set<AccessPath>
}

interface MethodAliasInfo {
    fun computeAliases(): List<StmtAliasInfo>
}

@OptIn(ExperimentalUnsignedTypes::class)
class StmtAliasInfoImpl(
    val baseToAlloc: IntArray,
    val allocToFields: Array<ULongArray>,
    val method: MethodAliasInfoImpl,
) : StmtAliasInfo {
    internal companion object {
        internal const val NOT_PROCESSED = -1
        internal const val MULTIPLE_EDGE = -2

        internal const val ELEMENT_ACCESSOR = -3

        internal fun merge(first: Int, second: Int): Int = when {
            first == NOT_PROCESSED -> second
            second == NOT_PROCESSED -> first
            first == MULTIPLE_EDGE -> MULTIPLE_EDGE
            second == MULTIPLE_EDGE -> MULTIPLE_EDGE
            first == second -> first
            else -> MULTIPLE_EDGE
        }

        internal fun wrap(string: Int, alloc: Int): ULong {
            return (string.toULong() shl Int.SIZE_BITS) or alloc.toULong()
        }

        internal fun unwrap(edge: ULong): Pair<Int, Int> {
            val string = (edge shr Int.SIZE_BITS).toInt()
            val allocation = (edge and UInt.MAX_VALUE.toULong()).toInt()
            return Pair(string, allocation)
        }
    }

    internal fun merge(other: StmtAliasInfoImpl): StmtAliasInfoImpl {
        val merged = StmtAliasInfoImpl(
            baseToAlloc = IntArray(method.bases.size) { NOT_PROCESSED },
            allocToFields = Array(method.allocations.size) { ulongArrayOf() },
            method = method
        )

        for (i in baseToAlloc.indices) {
            merged.baseToAlloc[i] = merge(baseToAlloc[i], other.baseToAlloc[i])
        }
        for (i in allocToFields.indices) {
            val toFieldsMap = mutableMapOf<Int, Int>()
            allocToFields[i].forEach {
                val (s, a) = unwrap(it)
                toFieldsMap.merge(s, a, Companion::merge)
            }
            other.allocToFields[i].forEach {
                val (s, a) = unwrap(it)
                toFieldsMap.merge(s, a, Companion::merge)
            }
            merged.allocToFields[i] = toFieldsMap
                .map { (string, alloc) -> wrap(string, alloc) }
                .toULongArray()
        }

        return merged
    }

    private fun trace(path: AccessPath): Pair<MutableList<Int>, MutableList<Int>> {
        val base = method.baseMap[path.base]
            ?: error("Unknown path base: ${path.base}")
        var node = baseToAlloc[base]

        val nodes = mutableListOf(node)
        val strings = mutableListOf<Int>()
        for (accessor in path.accesses) {
            if (node == NOT_PROCESSED || node == MULTIPLE_EDGE) {
                return Pair(nodes, strings)
            }

            when (accessor) {
                is ElementAccessor -> {
                    nodes.add(MULTIPLE_EDGE)
                    strings.add(ELEMENT_ACCESSOR)
                }

                is FieldAccessor -> {
                    val string = method.stringMap[accessor.name]
                        ?: error("Unknown field name: ${accessor.name}")
                    strings.add(string)

                    node = allocToFields[node]
                        .singleOrNull { e -> unwrap(e).first == string }
                        ?.let { e -> unwrap(e).second }
                        ?: run {
                            nodes.add(NOT_PROCESSED)
                            return Pair(nodes, strings)
                        }
                    nodes.add(node)
                }
            }
        }
        return Pair(nodes, strings)
    }

    private fun assign(lhv: AccessPath, rhv: AccessPath): StmtAliasInfoImpl {
        val (rhvNodes, _) = trace(rhv)
        val newAlloc = rhvNodes.last()
        return assign(lhv, newAlloc)
    }

    private fun assign(lhv: AccessPath, newAlloc: Int): StmtAliasInfoImpl {
        val (lhvNodes, lhvEdges) = trace(lhv)
        val from = lhvNodes.reversed().getOrNull(1)
        if (from != null) {
            val updated = StmtAliasInfoImpl(
                baseToAlloc = baseToAlloc,
                allocToFields = allocToFields.copyOf(),
                method = method,
            )

            val str = lhvEdges.last()
            val edgeIndex = allocToFields[from].indexOfFirst {
                val (s, _) = unwrap(it)
                s == str
            }
            if (edgeIndex == -1) {
                updated.allocToFields[from] = allocToFields[from].toMutableList().apply {
                    add(wrap(str, newAlloc))
                }.toULongArray()
            } else {
                updated.allocToFields[from] = allocToFields[from].copyOf().apply {
                    set(edgeIndex, wrap(str, newAlloc))
                }
            }

            return updated
        } else {
            val updated = StmtAliasInfoImpl(
                baseToAlloc = baseToAlloc.copyOf(),
                allocToFields = allocToFields,
                method = method,
            )

            val base = method.baseMap[lhv.base]
                ?: error("Unknown path base: ${lhv.base}")
            updated.baseToAlloc[base] = newAlloc

            return updated
        }
    }

    internal fun applyStmt(stmt: EtsStmt): StmtAliasInfoImpl {
        if (stmt !is EtsAssignStmt) {
            return this
        }
        return when (val rhv = stmt.rhv) {
            is EtsParameterRef -> {
                val alloc = method.allocationMap[Allocation.Arg(rhv.index)]
                    ?: error("Unknown parameter ref in stmt: $stmt")
                assign(stmt.lhv.toPath(), alloc)
            }

            is EtsThis -> {
                val alloc = method.allocationMap[Allocation.This]
                    ?: error("Uninitialized this in stmt: $stmt")
                assign(stmt.lhv.toPath(), alloc)
            }

            is EtsInstanceFieldRef, is EtsStaticFieldRef -> {
                val (rhvNodes, _) = trace(rhv.toPath())
                val alloc = rhvNodes.last()
                if (alloc == NOT_PROCESSED) {
                    val fieldAlloc = method.allocationMap[Allocation.Imm(stmt)]
                        ?: error("Unknown allocation in stmt: $stmt")
                    this
                        .assign(rhv.toPath(), fieldAlloc)
                        .assign(stmt.lhv.toPath(), fieldAlloc)
                } else {
                    assign(stmt.lhv.toPath(), alloc)
                }
            }

            is EtsLocal -> {
                assign(stmt.lhv.toPath(), rhv.toPath())
            }

            is EtsCastExpr -> {
                assign(stmt.lhv.toPath(), rhv.arg.toPath())
            }

            is EtsConstant, is EtsUnaryExpr, is EtsBinaryExpr, is EtsArrayAccess, is EtsInstanceOfExpr -> {
                val imm = method.allocationMap[Allocation.Expr(stmt)]
                    ?: error("Unknown constant in stmt: $stmt")
                assign(stmt.lhv.toPath(), imm)
            }

            is EtsCallExpr -> {
                val callResult = method.allocationMap[Allocation.CallResult(stmt)]
                    ?: error("Unknown call in stmt: $stmt")
                assign(stmt.lhv.toPath(), callResult)
            }

            is EtsNewExpr, is EtsNewArrayExpr -> {
                val new = method.allocationMap[Allocation.New(stmt)]
                    ?: error("Unknown new in stmt: $stmt")
                assign(stmt.lhv.toPath(), new)
            }

            else -> {
                error("Unprocessable rhs in stmt: $stmt")
            }
        }
    }

    private val invertedAllocToField: Array<ULongArray> by lazy {
        val edgeLists = Array(method.allocations.size) { mutableListOf<ULong>() }
        allocToFields.forEachIndexed { from, edges ->
            edges.forEach { edge ->
                val (str, to) = unwrap(edge)
                if (to != NOT_PROCESSED && to != MULTIPLE_EDGE) {
                    edgeLists[to].add(wrap(str, from))
                }
            }
        }
        edgeLists.map { it.toULongArray() }.toTypedArray()
    }

    private val invertedAllocToBase: Array<IntArray> by lazy {
        val edges = Array(method.allocations.size) { mutableListOf<Int>() }
        baseToAlloc.forEachIndexed { base, alloc ->
            if (alloc != NOT_PROCESSED && alloc != MULTIPLE_EDGE) {
                edges[alloc].add(base)
            }
        }
        edges.map { it.toIntArray() }.toTypedArray()
    }

    private class PathNode(
        val alloc: Int,
        val edge: Pair<Int, PathNode>?,
    ) {
        val parent: PathNode?
            get() = edge?.second

        val string: Int?
            get() = edge?.first

        fun traceContains(other: Int): Boolean =
            alloc == other || (parent?.traceContains(other) ?: false)
    }

    private fun accessors(node: PathNode): List<Accessor> {
        var cur = node
        val accessors = mutableListOf<Accessor>()
        while (true) {
            val str = cur.string
            if (str == null) {
                break
            }

            accessors.add(FieldAccessor(method.strings[str]))
            cur = cur.parent
                ?: error("If the property is defined, the parent should exist")
        }
        return accessors
    }

    override fun getAliases(path: AccessPath): Set<AccessPath> {
        val alloc = trace(path).first.last()
        if (alloc == NOT_PROCESSED || alloc == MULTIPLE_EDGE) {
            return setOf(path)
        }

        val queue = ArrayDeque<PathNode>()
        queue.addLast(PathNode(alloc, null))
        val paths = mutableListOf(path)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            invertedAllocToBase[node.alloc].forEach { base ->
                paths.add(
                    AccessPath(
                        base = method.bases[base],
                        accesses = accessors(node)
                    )
                )
            }

            invertedAllocToField[node.alloc].forEach {
                val (string, to) = unwrap(it)
                if (!node.traceContains(to)) {
                    queue.addLast(PathNode(to, Pair(string, node)))
                }
            }
        }

        return paths.toSet()
    }
}

class MethodAliasInfoImpl(
    val method: EtsMethod,
) : MethodAliasInfo {
    sealed interface Allocation {
        data class New(val stmt: EtsStmt) : Allocation
        data class CallResult(val stmt: EtsStmt) : Allocation
        data class Arg(val index: Int) : Allocation
        object This : Allocation
        data class Imm(val stmt: EtsStmt) : Allocation
        data class Expr(val stmt: EtsStmt) : Allocation
        data class Static(val clazz: EtsClassSignature) : Allocation
    }

    val stringMap = mutableMapOf<String, Int>()
    val strings = mutableListOf<String>()

    val allocationMap = mutableMapOf<Allocation, Int>()
    val allocations = mutableListOf<Allocation>()

    val baseMap = mutableMapOf<AccessPathBase, Int>()
    val bases = mutableListOf<AccessPathBase>()

    private fun newString(str: String) {
        stringMap.computeIfAbsent(str) {
            strings.add(str)
            stringMap.size
        }
    }

    private fun newAllocation(allocation: Allocation) {
        allocationMap.computeIfAbsent(allocation) {
            allocations.add(allocation)
            allocationMap.size
        }
    }

    private fun newBase(base: AccessPathBase) {
        baseMap.computeIfAbsent(base) {
            bases.add(base)
            baseMap.size
        }

        when (base) {
            is AccessPathBase.Local -> {
                newString(base.name)
            }

            is AccessPathBase.This -> {
                newAllocation(Allocation.This)
            }

            is AccessPathBase.Arg -> {
                newAllocation(Allocation.Arg(base.index))
            }

            is AccessPathBase.Static -> {
                newAllocation(Allocation.Static(base.clazz))
            }

            is AccessPathBase.Const -> { /* TODO ?? may be some non-trivial */
            }
        }
    }

    private fun initEntity(entity: EtsEntity) {
        when (entity) {
            is EtsInstanceFieldRef -> {
                initEntity(entity.instance)
                newString(entity.field.name)
            }

            is EtsStaticFieldRef -> {
                newBase(AccessPathBase.Static(entity.field.enclosingClass))
                newString(entity.field.name)
            }

            is EtsArrayAccess -> {
                initEntity(entity.array)
                initEntity(entity.index)
            }

            is EtsLocal -> {
                newBase(AccessPathBase.Local(entity.name))
            }

            is EtsParameterRef -> {
                newBase(AccessPathBase.Arg(entity.index))
            }

            is EtsInstanceCallExpr -> {
                initEntity(entity.instance)
                newString(entity.method.name)
                entity.args.forEach { initEntity(it) }
            }
        }
    }

    private fun initMaps() {
        newAllocation(Allocation.This)
        newBase(AccessPathBase.This)

        for (stmt in method.cfg.stmts) {
            when (stmt) {
                is EtsAssignStmt -> {
                    initEntity(stmt.lhv)
                    initEntity(stmt.rhv)

                    when (stmt.lhv) {
                        is EtsInstanceFieldRef, is EtsStaticFieldRef -> {
                            newAllocation(Allocation.Imm(stmt))
                        }
                    }

                    when (val rhv = stmt.rhv) {
                        is EtsNewExpr, is EtsNewArrayExpr -> {
                            newAllocation(Allocation.New(stmt))
                        }

                        is EtsParameterRef -> {
                            newAllocation(Allocation.Arg(rhv.index))
                        }

                        is EtsCallExpr -> {
                            newAllocation(Allocation.CallResult(stmt))
                        }

                        is EtsInstanceFieldRef, is EtsStaticFieldRef -> {
                            newAllocation(Allocation.Imm(stmt))
                        }

                        is EtsCastExpr -> {}
                        is EtsConstant, is EtsUnaryExpr, is EtsBinaryExpr, is EtsArrayAccess, is EtsInstanceOfExpr -> {
                            newAllocation(Allocation.Expr(stmt))
                        }
                    }
                }

                is EtsCallStmt -> {
                    initEntity(stmt.expr)
                }
            }
        }
    }

    init {
        initMaps()
    }

    private val preAliases = mutableMapOf<EtsStmt, StmtAliasInfoImpl>()

    @OptIn(ExperimentalUnsignedTypes::class)
    @Suppress("UNCHECKED_CAST")
    override fun computeAliases(): List<StmtAliasInfo> {
        val visited: MutableSet<EtsStmt> = hashSetOf()
        val order: MutableList<EtsStmt> = mutableListOf()
        val preds: MutableMap<EtsStmt, MutableList<EtsStmt>> = hashMapOf()

        fun postOrderDfs(node: EtsStmt) {
            if (visited.add(node)) {
                for (next in method.cfg.successors(node)) {
                    if (next !in visited) {
                        preds.computeIfAbsent(next) { mutableListOf() } += node
                    }
                    postOrderDfs(next)
                }
                order += node
            }
        }

        val root = method.cfg.stmts[0]
        postOrderDfs(root)
        order.reverse()

        fun computePreAliases(stmt: EtsStmt): StmtAliasInfo {
            if (stmt in preAliases) return preAliases.getValue(stmt)

            val merged = preds[stmt]
                ?.map { preAliases.getValue(it).applyStmt(it) }
                ?.reduceOrNull { a, b -> a.merge(b) }
                ?: StmtAliasInfoImpl(
                    baseToAlloc = IntArray(bases.size) { StmtAliasInfoImpl.NOT_PROCESSED },
                    allocToFields = Array(allocations.size) { ulongArrayOf() },
                    method = this
                )

            preAliases[stmt] = merged
            return merged
        }

        val aliases = Array<StmtAliasInfo?>(method.cfg.stmts.size) { null }
        for (stmt in order) {
            aliases[stmt.location.index] = computePreAliases(stmt)
        }

        assert(!aliases.contains(null))
        return (aliases as Array<StmtAliasInfo>).toList()
    }
}

object NoStmtAliasInfo : StmtAliasInfo {
    override fun getAliases(path: AccessPath): Set<AccessPath> {
        return setOf(path)
    }
}

class NoMethodAliasInfo(val method: EtsMethod) : MethodAliasInfo {
    override fun computeAliases(): List<StmtAliasInfo> {
        return method.cfg.stmts.map { NoStmtAliasInfo }
    }
}
