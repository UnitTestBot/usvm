package org.usvm.reachability

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.usvm.api.reachability.cli.TargetTrace
import org.usvm.api.reachability.dto.TargetTreeNodeDto
import org.usvm.api.reachability.dto.TargetTypeDto
import org.usvm.api.reachability.dto.TargetsContainerDto
import org.usvm.api.reachability.dto.extractTargetTraces
import kotlin.io.path.Path

/**
 * Unit tests for parsing target traces from JSON files.
 * Tests all supported formats defined in Targets.kt schema.
 */
class TargetsTest {

    private val resourcePath = "src/test/resources/reachability/targets"

    // Helper function to load and parse targets from a file
    private fun loadTargets(fileName: String): TargetsContainerDto {
        val path = Path("$resourcePath/$fileName")
        return TargetsContainerDto.from(path)
    }

    // Helper function to count total targets in a tree
    private fun countTargetsInTree(node: TargetTreeNodeDto): Int {
        return 1 + node.children.sumOf { countTargetsInTree(it) }
    }

    @Test
    fun `test linear trace with object format`() {
        // Load: { "targets": [...] }
        val container = loadTargets("linear-trace-object.json")

        // Should be parsed as LinearTrace
        Assertions.assertTrue(container is TargetsContainerDto.LinearTrace)
        val linearTrace = container as TargetsContainerDto.LinearTrace

        // Verify number of targets
        Assertions.assertEquals(3, linearTrace.targets.size)

        // Verify target types
        Assertions.assertEquals(TargetTypeDto.INITIAL, linearTrace.targets[0].type)
        Assertions.assertEquals(TargetTypeDto.INTERMEDIATE, linearTrace.targets[1].type)
        Assertions.assertEquals(TargetTypeDto.FINAL, linearTrace.targets[2].type)

        // Verify locations
        Assertions.assertEquals("test.ts", linearTrace.targets[0].location.fileName)
        Assertions.assertEquals("TestClass", linearTrace.targets[0].location.className)
        Assertions.assertEquals("testMethod", linearTrace.targets[0].location.methodName)
    }

    @Test
    fun `test linear trace with array format`() {
        // Load: [ {...}, {...}, ... ] (deprecated format)
        val container = loadTargets("linear-trace-array.json")

        // Should be parsed as LinearTrace (transformed from array)
        Assertions.assertTrue(container is TargetsContainerDto.LinearTrace)
        val linearTrace = container as TargetsContainerDto.LinearTrace

        // Verify number of targets
        Assertions.assertEquals(3, linearTrace.targets.size)

        // Verify target types (default should be INTERMEDIATE if not specified)
        Assertions.assertEquals(TargetTypeDto.INITIAL, linearTrace.targets[0].type)
        Assertions.assertEquals(TargetTypeDto.INTERMEDIATE, linearTrace.targets[1].type)
        Assertions.assertEquals(TargetTypeDto.FINAL, linearTrace.targets[2].type)
    }

    @Test
    fun `test tree trace with single root`() {
        // Load: { "root": {...} }
        val container = loadTargets("tree-trace-single.json")

        // Should be parsed as TreeTrace
        Assertions.assertTrue(container is TargetsContainerDto.TreeTrace)
        val treeTrace = container as TargetsContainerDto.TreeTrace

        // Verify root
        Assertions.assertNotNull(treeTrace.root)
        Assertions.assertEquals(TargetTypeDto.INITIAL, treeTrace.root.target.type)

        // Verify tree structure
        Assertions.assertEquals(2, treeTrace.root.children.size)

        // First branch has 2 children (both final)
        Assertions.assertEquals(2, treeTrace.root.children[0].children.size)
        Assertions.assertEquals(TargetTypeDto.FINAL, treeTrace.root.children[0].children[0].target.type)
        Assertions.assertEquals(TargetTypeDto.FINAL, treeTrace.root.children[0].children[1].target.type)

        // Second branch has 1 child (final)
        Assertions.assertEquals(1, treeTrace.root.children[1].children.size)
        Assertions.assertEquals(TargetTypeDto.FINAL, treeTrace.root.children[1].children[0].target.type)

        // Total targets in tree should be 6 (1 root + 2 intermediate + 3 final)
        Assertions.assertEquals(6, countTargetsInTree(treeTrace.root))
    }

    @Test
    fun `test trace list with object format`() {
        // Load: { "traces": [...] }
        val container = loadTargets("trace-list-object.json")

        // Should be parsed as TraceList
        Assertions.assertTrue(container is TargetsContainerDto.TraceList)
        val traceList = container as TargetsContainerDto.TraceList

        // Verify number of traces
        Assertions.assertEquals(2, traceList.traces.size)

        // Both should be LinearTrace
        Assertions.assertTrue(traceList.traces[0] is TargetsContainerDto.LinearTrace)
        Assertions.assertTrue(traceList.traces[1] is TargetsContainerDto.LinearTrace)

        // Verify first trace
        val trace1 = traceList.traces[0] as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(2, trace1.targets.size)
        Assertions.assertEquals("test1.ts", trace1.targets[0].location.fileName)

        // Verify second trace
        val trace2 = traceList.traces[1] as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(2, trace2.targets.size)
        Assertions.assertEquals("test2.ts", trace2.targets[0].location.fileName)
    }

    @Test
    fun `test trace list with array format mixed traces`() {
        // Load: [ {...}, {...} ] with mixed trace types (deprecated)
        val container = loadTargets("trace-list-array-mixed.json")

        // Should be parsed as TraceList
        Assertions.assertTrue(container is TargetsContainerDto.TraceList)
        val traceList = container as TargetsContainerDto.TraceList

        // Verify number of traces
        Assertions.assertEquals(2, traceList.traces.size)

        // First should be LinearTrace
        Assertions.assertTrue(traceList.traces[0] is TargetsContainerDto.LinearTrace)
        val linearTrace = traceList.traces[0] as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(2, linearTrace.targets.size)

        // Second should be TreeTrace
        Assertions.assertTrue(traceList.traces[1] is TargetsContainerDto.TreeTrace)
        val treeTrace = traceList.traces[1] as TargetsContainerDto.TreeTrace
        Assertions.assertEquals(1, treeTrace.root.children.size)
    }

    @Test
    fun `test trace list with mixed linear and tree traces`() {
        // Load: { "traces": [ linear, tree, linear ] }
        val container = loadTargets("trace-list-mixed.json")

        // Should be parsed as TraceList
        Assertions.assertTrue(container is TargetsContainerDto.TraceList)
        val traceList = container as TargetsContainerDto.TraceList

        // Verify number of traces
        Assertions.assertEquals(3, traceList.traces.size)

        // First trace: LinearTrace
        Assertions.assertTrue(traceList.traces[0] is TargetsContainerDto.LinearTrace)
        val linear1 = traceList.traces[0] as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(3, linear1.targets.size)
        Assertions.assertEquals("mixed1.ts", linear1.targets[0].location.fileName)

        // Second trace: TreeTrace
        Assertions.assertTrue(traceList.traces[1] is TargetsContainerDto.TreeTrace)
        val tree = traceList.traces[1] as TargetsContainerDto.TreeTrace
        Assertions.assertEquals(2, tree.root.children.size)
        Assertions.assertEquals("mixed2.ts", tree.root.target.location.fileName)

        // Third trace: LinearTrace
        Assertions.assertTrue(traceList.traces[2] is TargetsContainerDto.LinearTrace)
        val linear2 = traceList.traces[2] as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(2, linear2.targets.size)
        Assertions.assertEquals("mixed3.ts", linear2.targets[0].location.fileName)
    }

    @Test
    fun `test minimal location fields`() {
        // Load trace with minimal required fields in LocationDto
        val container = loadTargets("minimal-location-fields.json")

        Assertions.assertTrue(container is TargetsContainerDto.LinearTrace)
        val linearTrace = container as TargetsContainerDto.LinearTrace

        Assertions.assertEquals(3, linearTrace.targets.size)

        // First target: has type but minimal location
        val target1 = linearTrace.targets[0]
        Assertions.assertEquals(TargetTypeDto.INITIAL, target1.type)
        Assertions.assertEquals("minimal.ts", target1.location.fileName)
        Assertions.assertEquals("MinimalClass", target1.location.className)
        Assertions.assertEquals("minimalMethod", target1.location.methodName)
        Assertions.assertEquals(null, target1.location.block)
        Assertions.assertEquals(null, target1.location.index)
        Assertions.assertEquals(null, target1.location.stmtType)

        // Second target: default type with only index
        val target2 = linearTrace.targets[1]
        Assertions.assertEquals(TargetTypeDto.INTERMEDIATE, target2.type) // default
        Assertions.assertEquals(1, target2.location.index)
        Assertions.assertEquals(null, target2.location.block)

        // Third target: has stmtType
        val target3 = linearTrace.targets[2]
        Assertions.assertEquals("ReturnStmt", target3.location.stmtType)
    }

    @Test
    fun `test extractTargetTraces from linear trace`() {
        val container = loadTargets("linear-trace-object.json")
        val traces = extractTargetTraces(container)

        // Should extract 1 trace
        Assertions.assertEquals(1, traces.size)

        // Should be TargetTrace.Linear
        Assertions.assertTrue(traces[0] is TargetTrace.Linear)
        val linearTrace = traces[0] as TargetTrace.Linear
        Assertions.assertEquals(3, linearTrace.targets.size)
    }

    @Test
    fun `test extractTargetTraces from tree trace`() {
        val container = loadTargets("tree-trace-single.json")
        val traces = extractTargetTraces(container)

        // Should extract 1 trace
        Assertions.assertEquals(1, traces.size)

        // Should be TargetTrace.Tree
        Assertions.assertTrue(traces[0] is TargetTrace.Tree)
        val treeTrace = traces[0] as TargetTrace.Tree
        Assertions.assertEquals(6, countTargetsInTree(treeTrace.root))
    }

    @Test
    fun `test extractTargetTraces from trace list`() {
        val container = loadTargets("trace-list-mixed.json")
        val traces = extractTargetTraces(container)

        // Should extract 3 traces
        Assertions.assertEquals(3, traces.size)

        // First: Linear
        Assertions.assertTrue(traces[0] is TargetTrace.Linear)
        val linear1 = traces[0] as TargetTrace.Linear
        Assertions.assertEquals(3, linear1.targets.size)

        // Second: Tree
        Assertions.assertTrue(traces[1] is TargetTrace.Tree)
        val tree = traces[1] as TargetTrace.Tree
        Assertions.assertEquals(4, countTargetsInTree(tree.root))

        // Third: Linear
        Assertions.assertTrue(traces[2] is TargetTrace.Linear)
        val linear2 = traces[2] as TargetTrace.Linear
        Assertions.assertEquals(2, linear2.targets.size)
    }

    @Test
    fun `test parsing from JSON string directly`() {
        val jsonString = """
            {
              "targets": [
                {
                  "type": "initial",
                  "location": {
                    "fileName": "string-test.ts",
                    "className": "StringTest",
                    "methodName": "testMethod",
                    "block": 0,
                    "index": 0
                  }
                }
              ]
            }
        """.trimIndent()

        val container = TargetsContainerDto.from(jsonString)

        Assertions.assertTrue(container is TargetsContainerDto.LinearTrace)
        val linearTrace = container as TargetsContainerDto.LinearTrace
        Assertions.assertEquals(1, linearTrace.targets.size)
        Assertions.assertEquals("string-test.ts", linearTrace.targets[0].location.fileName)
    }

    @Test
    fun `test total target count across all formats`() {
        // Linear trace object: 3 targets
        val linear1 = loadTargets("linear-trace-object.json")
        val linear1Traces = extractTargetTraces(linear1)
        Assertions.assertEquals(1, linear1Traces.size)
        Assertions.assertTrue(linear1Traces[0] is TargetTrace.Linear)
        Assertions.assertEquals(3, (linear1Traces[0] as TargetTrace.Linear).targets.size)

        // Tree trace: 6 targets total
        val tree = loadTargets("tree-trace-single.json")
        val treeTraces = extractTargetTraces(tree)
        Assertions.assertEquals(1, treeTraces.size)
        Assertions.assertTrue(treeTraces[0] is TargetTrace.Tree)
        Assertions.assertEquals(6, countTargetsInTree((treeTraces[0] as TargetTrace.Tree).root))

        // Trace list object: 2 traces, 2 targets each = 4 total
        val traceList1 = loadTargets("trace-list-object.json")
        val traceList1Traces = extractTargetTraces(traceList1)
        Assertions.assertEquals(2, traceList1Traces.size)

        // Mixed trace list: 3 traces (3 + 5 + 2 = 10 targets total)
        val mixed = loadTargets("trace-list-mixed.json")
        val mixedTraces = extractTargetTraces(mixed)
        Assertions.assertEquals(3, mixedTraces.size)
    }
}
