package org.usvm.ts.pbt

import java.nio.file.Path

internal fun testResourcePath(name: String): Path {
    val resource = requireNotNull(TestResourceMarker::class.java.getResource(name)) {
        "Missing test resource: $name"
    }

    require(resource.protocol == "file") { "Test resource is not a regular file-system path: $resource" }

    return Path.of(resource.toURI())
}

internal fun testResourcesRoot(): Path = requireNotNull(testResourcePath("/properties").parent)

private object TestResourceMarker
