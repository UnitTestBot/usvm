package org.usvm.mcp.tools

import org.usvm.mcp.exec.TsAnalysisRunner
import org.usvm.mcp.scene.SceneCache

/** Shared dependencies injected into every tool. */
class UsvmToolContext(
    val scenes: SceneCache,
    val runner: TsAnalysisRunner,
)
