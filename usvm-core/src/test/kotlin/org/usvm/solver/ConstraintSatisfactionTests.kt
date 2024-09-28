package org.usvm.solver

import org.junit.jupiter.api.Test
import org.usvm.solver.URegularStringSolver.Companion.satisfyConstraints

class ConstraintSatisfactionTests {
    private class TestConstraint(
        override val keys: Set<Int>,
        val evaluate: (Map<Int, String>) -> Boolean
    ) : URegularStringSolver.Constraint<Int, String> {
        override fun eval(mapping: Map<Int, String>): Boolean = evaluate(mapping)
    }

    @Test
    fun testStringConstraints1() {
        val candidates = mapOf(
            1 to sequenceOf("a", "b", "c"),
            2 to sequenceOf("aba", "bac", "edd"),
            3 to sequenceOf("OLO", "ALLO", "BEDD"),
        )
        val constraint = TestConstraint(setOf(1,2,3)) { it[1]?.uppercase() + it[2]?.uppercase() == it[3] }
        val assignment = satisfyConstraints(listOf(constraint), candidates, 50)
        assert(assignment is USatResult)
        assignment as USatResult
        assert(assignment.model[1] == "b")
        assert(assignment.model[2] == "edd")
        assert(assignment.model[3] == "BEDD")
    }

    @Test
    fun testStringConstraints2() {
        val candidates = mapOf(
            1 to sequenceOf("a", "b", "c"),
            2 to sequenceOf("aba", "bac", "edd"),
            3 to sequenceOf("OLO", "ALLO", "BEDA"),
        )
        val constraint = TestConstraint(setOf(1,2,3)) { it[1]?.uppercase() + it[2]?.uppercase() == it[3] }
        val assignment = satisfyConstraints(listOf(constraint), candidates, 50)
        assert(assignment is UUnsatResult)
    }
}