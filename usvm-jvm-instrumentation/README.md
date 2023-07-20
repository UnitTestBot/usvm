# Instrumentation module of the usvm project

Features:
* Execution and state building for target method
* Trace in jacodb instruction via dynamic instrumentation
* Static rollback
* Run with timeout

TODO:
* Mocks
* Sandbox
* Concolic trace

Input:
* classpath of analyzing project + UTest (see Tests and org.usvm.instrumentation.testcase.api.api.kt)

Output:
* Execution state (see org.usvm.instrumentation.testcase.api.response.kt)

Example of module usage for simple classes and guava located in tests