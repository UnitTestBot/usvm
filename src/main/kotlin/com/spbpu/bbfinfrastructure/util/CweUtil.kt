package com.spbpu.bbfinfrastructure.util

import java.io.File

object CweUtil {
    private fun traverseMap(map: Map<Int, Int?>, key: Int?, res: List<Int>): List<Int> {
        if (map[key] == null) return res
        return traverseMap(map, map[key], res + map[key]!!)
    }

    private fun traverseMapReversed(map: Map<Int, Int?>, key: Int?, res: MutableSet<Int>) {
        val children = map.entries.filter { it.value == key }.map { it.key }
        if (children.isEmpty()) return
        res.addAll(children)
        children.forEach { traverseMapReversed(map, it, res) }
    }

    private val cwes = File("lib/1000.csv").readText().split("\n").drop(1).dropLast(1).associate {
        val cwe = it.substringBefore(",").toInt()
        val childOf = it.substringAfter("ChildOf:CWE ID:").substringBefore(":").toIntOrNull()
        cwe to childOf
    }

    fun getCwePathOf(cwe: Int): List<Int> = traverseMap(cwes, cwe, listOf())
    fun getCweChildrenOf(cwe: Int): Set<Int> {
        val res = mutableSetOf<Int>()
        traverseMapReversed(cwes, cwe, res)
        return res
    }
}