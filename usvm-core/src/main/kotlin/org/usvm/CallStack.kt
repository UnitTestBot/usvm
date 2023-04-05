package org.usvm

import java.util.Stack

data class UCallStackFrame<Method, Statement>(val method: Method, val returnSite: Statement?)

class UCallStack<Method, Statement> private constructor(private val stack: Stack<UCallStackFrame<Method, Statement>>) :
    Collection<UCallStackFrame<Method, Statement>> by stack {
    constructor() : this(Stack())
    constructor(method: Method) : this(
        Stack<UCallStackFrame<Method, Statement>>().apply {
            val firstFrame = UCallStackFrame(method, null as Statement?)
            push(firstFrame)
        }
    )

    fun pop(): Statement? = stack.pop().returnSite
    fun push(method: Method, returnSite: Statement?) {
        stack.push(UCallStackFrame(method, returnSite))
    }

    fun clone(): UCallStack<Method, Statement> {
        val newStack = Stack<UCallStackFrame<Method, Statement>>()
        newStack.ensureCapacity(stack.size)
        newStack.addAll(stack)
        return UCallStack(newStack)
    }
}
