package org.usvm.language

sealed interface ProgramException {
    val stmt: Stmt
}

class DivisionByZero(
    override val stmt: Stmt
) : ProgramException {
    override fun toString(): String = "DivisionByZero(stmt=$stmt)"
}

class IndexOutOfBounds(
    override val stmt: Stmt,
    val size: Int,
    val idx: Int,
) : ProgramException {
    override fun toString(): String = "IndexOutOfBounds(stmt=$stmt, size=$size, idx=$idx)"
}

class NegativeArraySize(
    override val stmt: Stmt,
    val size: Int,
    val actualSize: Int,
) : ProgramException {
    override fun toString(): String = "NegativeArraySize(stmt=$stmt, size=$size, actualSize=$actualSize)"
}

class UnsuccessfulCast(
    override val stmt: Stmt,
    val expectedType: SampleType,
    val actualType: SampleType,
) : ProgramException {
    override fun toString(): String =
        "UnsuccessfulCast(stmt=$stmt, expectedType=$expectedType, actualType=$actualType)"
}

class NullPointerDereference(
    override val stmt: Stmt,
) : ProgramException {
    override fun toString(): String = "NullPointerDereference(stmt=$stmt)"
}