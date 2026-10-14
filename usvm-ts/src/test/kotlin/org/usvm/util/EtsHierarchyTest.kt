package org.usvm.util

import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EtsHierarchyTest {
    @Test
    fun `cyclic ancestry terminates and keeps first encounter order`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "cycle.ts")
        val aSignature = EtsClassSignature(name = "A", file = fileSignature)
        val bSignature = EtsClassSignature(name = "B", file = fileSignature)
        val unrelatedSignature = EtsClassSignature(name = "Unrelated", file = fileSignature)
        val a = EtsClassImpl(
            signature = aSignature,
            fields = emptyList(),
            methods = emptyList(),
            superClass = bSignature,
        )
        val b = EtsClassImpl(
            signature = bSignature,
            fields = emptyList(),
            methods = emptyList(),
            superClass = aSignature,
        )
        val unrelated = EtsClassImpl(
            signature = unrelatedSignature,
            fields = emptyList(),
            methods = emptyList(),
        )
        val file = EtsFile(signature = fileSignature, classes = listOf(a, b, unrelated), namespaces = emptyList())
        val hierarchy = EtsHierarchy(EtsScene(projectFiles = listOf(file)))

        val unrelatedAncestors = hierarchy.getAncestors(unrelated)
        val aAncestors = hierarchy.getAncestors(a)
        val bAncestors = hierarchy.getAncestors(b)

        assertEquals(listOf(unrelated), unrelatedAncestors.toList())
        assertEquals(listOf(a, b), aAncestors.toList())
        assertEquals(listOf(b, a), bAncestors.toList())
    }
}
