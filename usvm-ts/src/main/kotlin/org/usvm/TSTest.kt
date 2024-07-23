package org.usvm

import org.jacodb.ets.base.EtsStmt

class TSTest(
    val parameters: List<Any>,
    val resultValue: Any?,
    val trace: List<EtsStmt>? = null,
)

class TSMethodCoverage
