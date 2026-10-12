package org.usvm.ts.calls

import org.usvm.UExpr

internal data class MaterializedCallsInput(val value: UExpr<*>, val snapshot: CallsInputSnapshot)
