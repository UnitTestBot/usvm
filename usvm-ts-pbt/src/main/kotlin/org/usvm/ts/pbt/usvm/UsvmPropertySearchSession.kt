package org.usvm.ts.pbt.usvm

import org.usvm.ts.pbt.backend.PropertyCoverageArtifact

/** Owns one original-property analysis, including its pending queue, completed paths and solver resources. */
class UsvmPropertySearchSession internal constructor(
    advanceSearch: (ULong?) -> UsvmPropertySearchResult,
    searchFinished: () -> Boolean,
    applyCoverage: (PropertyCoverageArtifact) -> UsvmCoverageUpdateResult,
    release: () -> Unit,
) : AutoCloseable {
    private var advanceSearch: ((ULong?) -> UsvmPropertySearchResult)? = advanceSearch
    private var searchFinished: (() -> Boolean)? = searchFinished
    private var applyCoverage: ((PropertyCoverageArtifact) -> UsvmCoverageUpdateResult)? = applyCoverage
    private var release: (() -> Unit)? = release
    private var advancing = false

    val isFinished: Boolean
        get() = searchFinished?.invoke() ?: true

    /** Advances the retained machine; a phase yield reports IN_PROGRESS rather than a completed search. */
    fun advance(maxSteps: ULong? = null): UsvmPropertySearchResult {
        val advance = checkNotNull(advanceSearch) { "Property search session is closed" }
        check(!advancing) { "Property search session is already advancing" }

        advancing = true
        var succeeded = false
        try {
            val result = advance(maxSteps)
            succeeded = true
            return result
        } finally {
            advancing = false
            if (!succeeded) close()
        }
    }

    /** Rejects incompatible evidence without changing the declared predicate or its path constraints. */
    fun applyCoverage(artifact: PropertyCoverageArtifact): UsvmCoverageUpdateResult {
        val apply = checkNotNull(applyCoverage) { "Property search session is closed" }
        check(!advancing) { "Cannot apply coverage while property search is advancing" }
        return apply(artifact)
    }

    override fun close() {
        check(!advancing) { "Cannot close an advancing property search session" }
        val releaseSearch = release ?: return

        advanceSearch = null
        searchFinished = null
        applyCoverage = null
        release = null
        releaseSearch()
    }
}
