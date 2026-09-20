package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import java.util.Properties

/** Dependency versions generated from the packaged fast-check adapter lockfile. */
internal object FastCheckRuntimeMetadata {
    private val properties = Properties().apply {
        val resource = checkNotNull(
            FastCheckRuntimeMetadata::class.java.getResourceAsStream(RUNTIME_METADATA_RESOURCE),
        ) {
            "Missing generated fast-check runtime metadata: $RUNTIME_METADATA_RESOURCE"
        }
        resource.use(::load)
    }

    val fastCheckVersion: String = requireVersion("fast-check.version")

    val coverageCollector = CoverageCollectorIdentity(
        id = "c8",
        version = requireVersion("c8.version"),
    )

    private fun requireVersion(name: String): String = properties.getProperty(name)
        ?.takeIf(String::isNotBlank)
        ?: error("Missing fast-check runtime dependency version: $name")

    private const val RUNTIME_METADATA_RESOURCE = "/org/usvm/ts/pbt/fastcheck/runtime-dependencies.properties"
}
