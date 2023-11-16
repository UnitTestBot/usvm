package org.usvm.util

enum class JcTestResolverType {
    /**
     * Uses reflection to resolve objects.
     */
    INTERPRETER,
    /**
     * Uses concrete execution to resolve objects.
     */
    CONCRETE_EXECUTOR
}