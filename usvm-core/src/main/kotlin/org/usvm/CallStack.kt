package org.usvm

data class UCallStackFrame<Method, Statement>(
    val method: Method,
    val returnSite: Statement?,
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
}
