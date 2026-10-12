package org.usvm.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class McpErrorsTest {
    @Test
    fun `tool cancellation is rethrown without converting it to an error result`() = runBlocking {
        val cancellation = CancellationException("request cancelled")

        val caught = assertFailsWith<CancellationException> {
            runTool { throw cancellation }
        }

        assertSame(cancellation, caught)
    }
}
