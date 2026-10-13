package org.usvm.mcp

import org.jacodb.ets.utils.EtsIrProvider
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.usvm.mcp.scene.SceneCache
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class SceneCacheTest {
    @Test
    fun `bundled frontend loads TypeScript without ArkAnalyzer`(@TempDir directory: Path) {
        assumeTrue(EtsIrProvider.default() == EtsIrProvider.TS_FRONTEND)

        val source = directory.resolve("scene.ts")
        source.writeText("class SceneFixture { compute(input: number): number { return input + 1; } }")

        val scene = SceneCache().getScene(source.toString())

        val hasExpectedMethod = scene.projectAndSdkClasses.any { clazz ->
            clazz.name == "SceneFixture" && clazz.methods.any { it.name == "compute" }
        }

        assertTrue(hasExpectedMethod)
    }
}
