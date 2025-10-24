package org.usvm.reachability

import org.junit.jupiter.api.Test
import org.usvm.api.reachability.TargetTrace
import org.usvm.api.reachability.dto.TargetTreeNodeDto
import org.usvm.api.reachability.dto.TargetTypeDto
import org.usvm.api.reachability.dto.TargetsContainerDto
import org.usvm.api.reachability.dto.extractTargetTraces
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Unit tests for parsing target traces from JSON files.
 * Tests all supported formats defined in Targets.kt schema.
 */
class TargetsTest {

    // Helper function to load TargetsContainerDto from resource path
    private fun load(path: String): TargetsContainerDto {
        val resourcePath = getResourcePath("/" + path)
        return TargetsContainerDto.from(resourcePath)
    }

    // Helper function to count total targets in a tree
    private fun countTargetsInTree(node: TargetTreeNodeDto): Int {
        return 1 + node.children.sumOf { countTargetsInTree(it) }
    }

    @Test
    fun `test linear trace with object format`() {
        // Load: { "targets": [...] }
        val container = load("reachability/targets/linear-trace-object.json")

        // Should be parsed as LinearTrace
        assertIs<TargetsContainerDto.LinearTrace>(container)

        // Verify number of targets
        assertEquals(3, container.targets.size)

        // Verify target types
        assertEquals(TargetTypeDto.INITIAL, container.targets[0].type)
        assertEquals(TargetTypeDto.INTERMEDIATE, container.targets[1].type)
        assertEquals(TargetTypeDto.FINAL, container.targets[2].type)

        // Verify locations
        assertEquals("test.ts", container.targets[0].location.fileName)
        assertEquals("TestClass", container.targets[0].location.className)
        assertEquals("testMethod", container.targets[0].location.methodName)
    }

    @Test
    fun `test linear trace with array format`() {
        // Load: [ {...}, {...}, ... ] (deprecated format)
        val container = load("reachability/targets/linear-trace-array.json")

        // Should be parsed as LinearTrace (transformed from array)
        assertIs<TargetsContainerDto.LinearTrace>(container)

        // Verify number of targets
        assertEquals(3, container.targets.size)

        // Verify target types (default should be INTERMEDIATE if not specified)
        assertEquals(TargetTypeDto.INITIAL, container.targets[0].type)
        assertEquals(TargetTypeDto.INTERMEDIATE, container.targets[1].type)
        assertEquals(TargetTypeDto.FINAL, container.targets[2].type)
    }

    @Test
    fun `test tree trace with single root`() {
        // Load: { "root": {...} }
        val container = load("reachability/targets/tree-trace-single.json")

        // Should be parsed as TreeTrace
        assertIs<TargetsContainerDto.TreeTrace>(container)

        // Verify root
        assertNotNull(container.root)
        assertEquals(TargetTypeDto.INITIAL, container.root.target.type)

        // Verify tree structure
        assertEquals(2, container.root.children.size)

        // First branch has 2 children (both final)
        assertEquals(2, container.root.children[0].children.size)
        assertEquals(TargetTypeDto.FINAL, container.root.children[0].children[0].target.type)
        assertEquals(TargetTypeDto.FINAL, container.root.children[0].children[1].target.type)

        // Second branch has 1 child (final)
        assertEquals(1, container.root.children[1].children.size)
        assertEquals(TargetTypeDto.FINAL, container.root.children[1].children[0].target.type)

        // Total targets in tree should be 6 (1 root + 2 intermediate + 3 final)
        assertEquals(6, countTargetsInTree(container.root))
    }

    @Test
    fun `test trace list with object format`() {
        // Load: { "traces": [...] }
        val container = load("reachability/targets/trace-list-object.json")

        // Should be parsed as TraceList
        assertIs<TargetsContainerDto.TraceList>(container)

        // Verify number of traces
        assertEquals(2, container.traces.size)

        // Both should be LinearTrace
        val trace1 = container.traces[0]
        assertIs<TargetsContainerDto.LinearTrace>(trace1)
        val trace2 = container.traces[1]
        assertIs<TargetsContainerDto.LinearTrace>(trace2)

        // Verify first trace
        assertEquals(2, trace1.targets.size)
        assertEquals("test1.ts", trace1.targets[0].location.fileName)

        // Verify second trace
        assertEquals(2, trace2.targets.size)
        assertEquals("test2.ts", trace2.targets[0].location.fileName)
    }

    @Test
    fun `test trace list with array format mixed traces`() {
        // Load: [ {...}, {...} ] with mixed trace types (deprecated)
        val container = load("reachability/targets/trace-list-array-mixed.json")

        // Should be parsed as TraceList
        assertIs<TargetsContainerDto.TraceList>(container)

        // Verify number of traces
        assertEquals(2, container.traces.size)

        // First should be LinearTrace
        val linearTrace = container.traces[0]
        assertIs<TargetsContainerDto.LinearTrace>(linearTrace)
        assertEquals(2, linearTrace.targets.size)

        // Second should be TreeTrace
        val treeTrace = container.traces[1]
        assertIs<TargetsContainerDto.TreeTrace>(treeTrace)
        assertEquals(1, treeTrace.root.children.size)
    }

    @Test
    fun `test trace list with mixed linear and tree traces`() {
        // Load: { "traces": [ linear, tree, linear ] }
        val container = load("reachability/targets/trace-list-mixed.json")

        // Should be parsed as TraceList
        assertIs<TargetsContainerDto.TraceList>(container)

        // Verify number of traces
        assertEquals(3, container.traces.size)

        // First trace: LinearTrace
        val linear1 = container.traces[0]
        assertIs<TargetsContainerDto.LinearTrace>(linear1)
        assertEquals(3, linear1.targets.size)
        assertEquals("mixed1.ts", linear1.targets[0].location.fileName)

        // Second trace: TreeTrace
        val tree = container.traces[1]
        assertIs<TargetsContainerDto.TreeTrace>(tree)
        assertEquals(2, tree.root.children.size)
        assertEquals("mixed2.ts", tree.root.target.location.fileName)

        // Third trace: LinearTrace
        val linear2 = container.traces[2]
        assertIs<TargetsContainerDto.LinearTrace>(linear2)
        assertEquals(2, linear2.targets.size)
        assertEquals("mixed3.ts", linear2.targets[0].location.fileName)
    }

    @Test
    fun `test minimal location fields`() {
        // Load trace with minimal required fields in LocationDto
        val container = load("reachability/targets/minimal-location-fields.json")

        assertIs<TargetsContainerDto.LinearTrace>(container)

        assertEquals(3, container.targets.size)

        // First target: has type but minimal location
        val target1 = container.targets[0]
        assertEquals(TargetTypeDto.INITIAL, target1.type)
        assertEquals("minimal.ts", target1.location.fileName)
        assertEquals("MinimalClass", target1.location.className)
        assertEquals("minimalMethod", target1.location.methodName)
        assertEquals(null, target1.location.block)
        assertEquals(null, target1.location.index)
        assertEquals(null, target1.location.stmtType)

        // Second target: default type with only index
        val target2 = container.targets[1]
        assertEquals(TargetTypeDto.INTERMEDIATE, target2.type) // default
        assertEquals(1, target2.location.index)
        assertEquals(null, target2.location.block)

        // Third target: has stmtType
        val target3 = container.targets[2]
        assertEquals("ReturnStmt", target3.location.stmtType)
    }

    @Test
    fun `test extractTargetTraces from linear trace`() {
        val container = load("reachability/targets/linear-trace-object.json")
        val traces = extractTargetTraces(container)

        // Should extract 1 trace
        assertEquals(1, traces.size)

        // Should be TargetTrace.Linear
        val linearTrace = traces[0]
        assertIs<TargetTrace.Linear>(linearTrace)
        assertEquals(3, linearTrace.targets.size)
    }

    @Test
    fun `test extractTargetTraces from tree trace`() {
        val container = load("reachability/targets/tree-trace-single.json")
        val traces = extractTargetTraces(container)

        // Should extract 1 trace
        assertEquals(1, traces.size)

        // Should be TargetTrace.Tree
        val treeTrace = traces[0]
        assertIs<TargetTrace.Tree>(treeTrace)
        assertEquals(6, countTargetsInTree(treeTrace.root))
    }

    @Test
    fun `test extractTargetTraces from trace list`() {
        val container = load("reachability/targets/trace-list-mixed.json")
        val traces = extractTargetTraces(container)

        // Should extract 3 traces
        assertEquals(3, traces.size)

        // First: Linear
        val linear1 = traces[0]
        assertIs<TargetTrace.Linear>(linear1)
        assertEquals(3, linear1.targets.size)

        // Second: Tree
        val tree = traces[1]
        assertIs<TargetTrace.Tree>(tree)
        assertEquals(4, countTargetsInTree(tree.root))

        // Third: Linear
        val linear2 = traces[2]
        assertIs<TargetTrace.Linear>(linear2)
        assertEquals(2, linear2.targets.size)
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

        assertIs<TargetsContainerDto.LinearTrace>(container)
        assertEquals(1, container.targets.size)
        assertEquals("string-test.ts", container.targets[0].location.fileName)
    }

    @Test
    fun `test total target count across all formats`() {
        // Linear trace object: 3 targets
        val linear1 = load("reachability/targets/linear-trace-object.json")
        val linear1Traces = extractTargetTraces(linear1)
        assertEquals(1, linear1Traces.size)
        val linear1Trace0 = linear1Traces[0]
        assertIs<TargetTrace.Linear>(linear1Trace0)
        assertEquals(3, linear1Trace0.targets.size)

        // Tree trace: 6 targets total
        val tree = load("reachability/targets/tree-trace-single.json")
        val treeTraces = extractTargetTraces(tree)
        assertEquals(1, treeTraces.size)
        val treeTrace0 = treeTraces[0]
        assertIs<TargetTrace.Tree>(treeTrace0)
        assertEquals(6, countTargetsInTree(treeTrace0.root))

        // Trace list object: 2 traces, 2 targets each = 4 total
        val traceList1 = load("reachability/targets/trace-list-object.json")
        val traceList1Traces = extractTargetTraces(traceList1)
        assertEquals(2, traceList1Traces.size)
        val trace1 = traceList1Traces[0]
        assertIs<TargetTrace.Linear>(trace1)
        assertEquals(2, trace1.targets.size)
        val trace2 = traceList1Traces[1]
        assertIs<TargetTrace.Linear>(trace2)
        assertEquals(2, trace2.targets.size)

        // Mixed trace list: 3 traces (3 + 5 + 2 = 10 targets total)
        val mixed = load("reachability/targets/trace-list-mixed.json")
        val mixedTraces = extractTargetTraces(mixed)
        assertEquals(3, mixedTraces.size)
    }
}
