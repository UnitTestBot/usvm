package org.usvm

import java.util.*

data class UCallStackFrame<Method, Statement>(val method: Method, val returnSite: Statement?)

class UCallStack<Method, Statement> private constructor(private val stack: Stack<UCallStackFrame<Method, Statement>>) :
    Sequence<UCallStackFrame<Method, Statement>> {
    constructor(method: Method) : this(
        Stack<UCallStackFrame<Method, Statement>>().apply {
            val firstFrame = UCallStackFrame(method, null as Statement?)
            push(firstFrame)
        }
    )

    override fun iterator() = stack.iterator()

    fun pop(): Statement? = stack.pop().returnSite

    fun lastMethod(): Method = stack.lastElement().method

    fun push(method: Method, returnSite: Statement) {
        stack.push(UCallStackFrame(method, returnSite))
    }

    fun clone(): UCallStack<Method, Statement> {
        val newStack = Stack<UCallStackFrame<Method, Statement>>()
        newStack.ensureCapacity(stack.size)
        stack.forEach { newStack.push(it) }
        return UCallStack(newStack)
    }
}
