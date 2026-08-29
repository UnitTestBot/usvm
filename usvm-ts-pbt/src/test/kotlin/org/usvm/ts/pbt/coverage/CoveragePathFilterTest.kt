package org.usvm.ts.pbt.coverage

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoveragePathFilterTest {
    @Test
    fun `Unix filesystem root contains absolute descendants and produces relative candidates`() {
        val path = "/workspace/src/property.ts"

        assertTrue(isWithin(path = path, root = "/"))
        assertTrue(
            matchesCoveragePath(
                path = path,
                patterns = listOf("workspace/src/*.ts"),
                sourceRoots = listOf("/"),
            ),
        )
    }

    @Test
    fun `normalized Windows drive root contains descendants and produces relative candidates`() {
        val path = "C:/workspace/src/property.ts"

        assertTrue(isWithin(path = path, root = "C:/"))
        assertTrue(
            matchesCoveragePath(
                path = path,
                patterns = listOf("workspace/src/*.ts"),
                sourceRoots = listOf("C:/"),
            ),
        )
        assertFalse(isWithin(path = "D:/workspace/src/property.ts", root = "C:/"))
    }
}
