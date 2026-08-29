package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FastCheckRuntimeMetadataTest {
    @Test
    fun `runtime dependency versions are packaged from the adapter lockfile`() {
        val adapterRoot = FastCheckRuntime.executionEntryPoint().parent.parent.parent
        val packageLock = PropertyManifestJson.json
            .parseToJsonElement(Files.readString(adapterRoot.resolve("package-lock.json")))
            .jsonObject
        val packages = packageLock.getValue("packages").jsonObject
        val expectedFastCheckVersion = packages.getValue("node_modules/fast-check")
            .jsonObject
            .getValue("version")
            .jsonPrimitive
            .content
        val expectedC8Version = packages.getValue("node_modules/c8")
            .jsonObject
            .getValue("version")
            .jsonPrimitive
            .content

        val metadata = Properties().apply {
            val resource = assertNotNull(
                FastCheckRuntimeMetadataTest::class.java.getResourceAsStream(RUNTIME_METADATA_RESOURCE),
            )
            resource.use(::load)
        }
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

        assertEquals(expectedFastCheckVersion, metadata.getProperty("fast-check.version"))
        assertEquals(expectedC8Version, metadata.getProperty("c8.version"))
        assertEquals(expectedFastCheckVersion, backend.coverageCapability.backendVersion)
        assertEquals(expectedC8Version, backend.coverageCapability.collector?.version)
    }

    private companion object {
        const val RUNTIME_METADATA_RESOURCE = "/org/usvm/ts/pbt/fastcheck/runtime-dependencies.properties"
    }
}
