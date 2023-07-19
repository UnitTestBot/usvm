package org.usvm

data class UCallStackFrame<Method, Statement>(
    val method: Method,
    val returnSite: Statement?,
)

data class UStackTraceFrame<Method, Statement>(
    val method: Method,
    val instruction: Statement,
)

class UCallStack<Method, Statement> private constructor(
    private val stack: ArrayDeque<UCallStackFrame<Method, Statement>>,
) : Collection<UCallStackFrame<Method, Statement>> by stack {
    constructor() : this(ArrayDeque())
    constructor(method: Method) : this(
        ArrayDeque<UCallStackFrame<Method, Statement>>().apply {
            val firstFrame = UCallStackFrame(method, null as Statement?)
            add(firstFrame)
        }
    )

    fun pop(): Statement? = stack.removeLast().returnSite

    fun lastMethod(): Method = stack.last().method

    fun push(method: Method, returnSite: Statement?) {
        stack.add(UCallStackFrame(method, returnSite))
    }

    fun clone(): UCallStack<Method, Statement> {
        val newStack = ArrayDeque<UCallStackFrame<Method, Statement>>()
        newStack.addAll(stack)
        return UCallStack(newStack)
    }

    fun stackTrace(currentInstruction: Statement): List<UStackTraceFrame<Method, Statement>> {
        val stacktrace: MutableList<UStackTraceFrame<Method, Statement>> = stack
            .zipWithNext()
            .mapTo(mutableListOf()) {
                UStackTraceFrame(it.first.method, it.second.returnSite!!)
            }

        return stacktrace + UStackTraceFrame(stack.last().method, currentInstruction)
    }

    override fun toString(): String {
        var frameCounter = 0

        return joinToString(
            prefix = "Call stack (contains $size frame${if (size > 1) "s" else ""}):${System.lineSeparator()}",
            separator = System.lineSeparator()
        ) { "\t${frameCounter++}: $it" }
    }
}
