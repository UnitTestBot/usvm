package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.neq
import org.jacodb.ets.dsl.or
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.thisRef
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.graph.linearize
import org.jacodb.ets.graph.toEtsBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy

class Call : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Call.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

    @Test
    fun `test simpleCall`() {
        val method = getMethod("Call", "simpleCall")
        discoverProperties<TSObject.TSNumber>(
            method = method,
            { r -> r.number == 42.0 },
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }
}
