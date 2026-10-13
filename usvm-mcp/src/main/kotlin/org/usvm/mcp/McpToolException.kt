package org.usvm.mcp

/** An actionable tool failure whose cause remains available for diagnosis. */
class McpToolException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
