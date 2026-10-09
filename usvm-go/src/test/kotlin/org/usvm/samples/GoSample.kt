package org.usvm.samples

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class GoSample(val method: String, val fixture: String = "examples")
