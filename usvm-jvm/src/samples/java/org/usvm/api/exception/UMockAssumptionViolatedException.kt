package org.usvm.api.exception


class UMockAssumptionViolatedException : RuntimeException() {
    override val message: String
        get() = "UMock assumption violated"
}

